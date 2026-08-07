package com.voyage.app.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.security.JwtService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerNotificationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;
  @Autowired HotelRepository hotelRepository;
  @Autowired InventoryService inventoryService;

  private Hotel hotel;
  private String userToken;

  @BeforeEach
  void setUp() {
    hotel = hotelRepository.save(new Hotel("Booking Hotel", "Rome", 210.0));
    User user =
        userRepository.save(
            new User(
                "booking-controller-user",
                "booking-controller-user@test.com",
                passwordEncoder.encode("password123"),
                Role.CUSTOMER));
    userToken = "Bearer " + jwtService.generateToken(user);
    LocalDate checkIn = LocalDate.now().plusDays(2);
    inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn, 2);
    inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn.plusDays(1), 2);
  }

  @Test
  void createBooking_createsNotificationAndAllowsMarkRead() throws Exception {
    LocalDate checkIn = LocalDate.now().plusDays(2);
    mockMvc
        .perform(
            post("/api/v1/bookings")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateBookingRequest(
                            hotel.getId(),
                            RoomType.DOUBLE,
                            checkIn,
                            checkIn.plusDays(2),
                            "approve"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    mockMvc
        .perform(get("/api/v1/bookings").header("Authorization", userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].hotelName").value("Booking Hotel"));

    String notificationBody =
        mockMvc
            .perform(get("/api/v1/notifications/me").header("Authorization", userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].type").value("BOOKING_CONFIRMED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    long notificationId =
        objectMapper.readTree(notificationBody).get("content").get(0).get("id").asLong();
    mockMvc
        .perform(
            patch("/api/v1/notifications/{id}/read", notificationId)
                .header("Authorization", userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.read").value(true));
  }
}
