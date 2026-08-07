package com.voyage.app.booking;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.security.JwtService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerCancellationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired HotelRepository hotelRepository;
    @Autowired InventoryService inventoryService;
    @Autowired RoomInventoryRepository roomInventoryRepository;

    private Hotel hotel;
    private String userToken;
    private LocalDate checkIn;

    @BeforeEach
    void setUp() {
        hotel = hotelRepository.save(new Hotel("Cancelable Hotel", "Madrid", 175.0));
        User user = userRepository.save(new User("cancel-controller-user", "cancel-controller-user@test.com", passwordEncoder.encode("password123"), Role.CUSTOMER));
        userToken = "Bearer " + jwtService.generateToken(user);
        checkIn = LocalDate.now().plusDays(4);
        inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn, 1);
        inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn.plusDays(1), 1);
    }

    @Test
    void cancelBooking_restoresInventory() throws Exception {
        String bookingBody = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                hotel.getId(),
                                RoomType.DOUBLE,
                                checkIn,
                                checkIn.plusDays(2),
                                "approve"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long bookingId = objectMapper.readTree(bookingBody).get("id").asLong();

        mockMvc.perform(delete("/api/v1/bookings/{id}", bookingId)
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(1, roomInventoryRepository.findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn, RoomType.DOUBLE).orElseThrow().getAvailableRooms());
        assertEquals(1, roomInventoryRepository.findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn.plusDays(1), RoomType.DOUBLE).orElseThrow().getAvailableRooms());
    }
}