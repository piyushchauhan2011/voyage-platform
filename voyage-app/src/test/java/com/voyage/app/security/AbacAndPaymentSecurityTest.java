package com.voyage.app.security;

import com.voyage.app.booking.CreateBookingRequest;
import com.voyage.app.booking.RatePlan;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.CreateInventoryRequest;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.payment.PaymentRepository;
import com.voyage.app.payment.PaymentStatus;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AbacAndPaymentSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired HotelRepository hotelRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String bearerTokenFor(Role role, String username) {
        User user = new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
        User saved = userRepository.save(user);
        return "Bearer " + jwtService.generateToken(saved);
    }

    private User userByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    @Test
    void managerCannotEditAnotherManagersHotel() throws Exception {
        String managerAToken = bearerTokenFor(Role.HOTEL_MANAGER, "mgr-a-edit");
        String managerBToken = bearerTokenFor(Role.HOTEL_MANAGER, "mgr-b-edit");

        MvcResult create = mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", managerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("A Hotel", "Paris", 100.0))))
                .andExpect(status().isCreated())
                .andReturn();
        long hotelId = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/v1/hotels/" + hotelId)
                        .header("Authorization", managerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Hijacked", "Paris", 100.0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void freeManagerBlockedOnInventory_proAllowsInventory() throws Exception {
        String adminToken = bearerTokenFor(Role.ADMIN, "admin-inv-plan");
        String managerToken = bearerTokenFor(Role.HOTEL_MANAGER, "mgr-inv-plan");
        User manager = userByUsername("mgr-inv-plan");

        MvcResult create = mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Plan Hotel", "Berlin", 120.0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saasPlan").value("FREE"))
                .andReturn();
        long hotelId = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asLong();
        LocalDate date = LocalDate.now().plusDays(40);

        mockMvc.perform(post("/api/v1/inventory")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInventoryRequest(hotelId, RoomType.DOUBLE, date, 3))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/hotels/" + hotelId + "/management")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerId\":" + manager.getId() + ",\"saasPlan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saasPlan").value("PRO"));

        mockMvc.perform(post("/api/v1/inventory")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInventoryRequest(hotelId, RoomType.DOUBLE, date, 3))))
                .andExpect(status().isCreated());
    }

    @Test
    void freeManagerCannotCreateSecondHotel_untilAdminUpgradesPlan() throws Exception {
        String adminToken = bearerTokenFor(Role.ADMIN, "admin-hotel-cap");
        String managerToken = bearerTokenFor(Role.HOTEL_MANAGER, "mgr-hotel-cap");
        User manager = userByUsername("mgr-hotel-cap");

        mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("First", "Rome", 90.0))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Second", "Rome", 95.0))))
                .andExpect(status().isForbidden());

        long firstId = hotelRepository.findByManager_Id(manager.getId()).getFirst().getId();
        mockMvc.perform(patch("/api/v1/hotels/" + firstId + "/management")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerId\":" + manager.getId() + ",\"saasPlan\":\"PRO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Second", "Rome", 95.0))))
                .andExpect(status().isCreated());
    }

    @Test
    void customerNonRefundableDenied_enterpriseManagerCanRefund() throws Exception {
        String adminToken = bearerTokenFor(Role.ADMIN, "admin-refund-abac");
        String managerToken = bearerTokenFor(Role.HOTEL_MANAGER, "mgr-refund-abac");
        String customerToken = bearerTokenFor(Role.CUSTOMER, "cust-refund-abac");
        User manager = userByUsername("mgr-refund-abac");

        MvcResult hotelResult = mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Refund Hotel", "Oslo", 200.0))))
                .andExpect(status().isCreated())
                .andReturn();
        long hotelId = objectMapper.readTree(hotelResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/v1/hotels/" + hotelId + "/management")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerId\":" + manager.getId() + ",\"saasPlan\":\"ENTERPRISE\"}"))
                .andExpect(status().isOk());

        LocalDate checkIn = LocalDate.now().plusDays(50);
        LocalDate checkOut = checkIn.plusDays(1);
        mockMvc.perform(post("/api/v1/inventory")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInventoryRequest(hotelId, RoomType.SINGLE, checkIn, 2))))
                .andExpect(status().isCreated());

        MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                hotelId, RoomType.SINGLE, checkIn, checkOut, "approve", RatePlan.NON_REFUNDABLE))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratePlan").value("NON_REFUNDABLE"))
                .andReturn();
        long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(0, objectMapper.readTree(bookingResult.getResponse().getContentAsString())
                .get("totalPrice").decimalValue().compareTo(new java.math.BigDecimal("170.00")));

        MvcResult paymentResult = mockMvc.perform(get("/api/v1/payments")
                        .param("bookingId", String.valueOf(bookingId))
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        long paymentId = objectMapper.readTree(paymentResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refund")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refund")
                        .header("Authorization", managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(paymentId).orElseThrow().getStatus());
    }

    @Test
    void flexibleCancelAutoRefunds_customerCannotViewOthersPayment() throws Exception {
        String adminToken = bearerTokenFor(Role.ADMIN, "admin-flex-cancel");
        String managerToken = bearerTokenFor(Role.HOTEL_MANAGER, "mgr-flex-cancel");
        String customerToken = bearerTokenFor(Role.CUSTOMER, "cust-flex-cancel");
        String otherToken = bearerTokenFor(Role.CUSTOMER, "cust-other-pay");
        User manager = userByUsername("mgr-flex-cancel");

        MvcResult hotelResult = mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Flex Hotel", "Lisbon", 100.0))))
                .andExpect(status().isCreated())
                .andReturn();
        long hotelId = objectMapper.readTree(hotelResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/v1/hotels/" + hotelId + "/management")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerId\":" + manager.getId() + ",\"saasPlan\":\"PRO\"}"))
                .andExpect(status().isOk());

        LocalDate checkIn = LocalDate.now().plusDays(60);
        LocalDate checkOut = checkIn.plusDays(2);
        mockMvc.perform(post("/api/v1/inventory")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInventoryRequest(hotelId, RoomType.DOUBLE, checkIn, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/inventory")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInventoryRequest(hotelId, RoomType.DOUBLE, checkIn.plusDays(1), 1))))
                .andExpect(status().isCreated());

        MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                hotelId, RoomType.DOUBLE, checkIn, checkOut, "approve", RatePlan.FLEXIBLE))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode bookingJson = objectMapper.readTree(bookingResult.getResponse().getContentAsString());
        long bookingId = bookingJson.get("id").asLong();
        assertTrue(bookingJson.get("totalPrice").decimalValue().compareTo(new java.math.BigDecimal("200.00")) == 0);

        long paymentId = objectMapper.readTree(mockMvc.perform(get("/api/v1/payments")
                        .param("bookingId", String.valueOf(bookingId))
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/bookings/" + bookingId)
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(paymentId).orElseThrow().getStatus());
    }
}
