package com.voyage.app.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
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
class InventoryControllerSecurityTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;
  @Autowired HotelRepository hotelRepository;

  private Hotel hotel;

  @BeforeEach
  void setUp() {
    hotel = hotelRepository.save(new Hotel("Inventory Hotel", "Berlin", 140.0));
  }

  private String bearerTokenFor(Role role, String username) {
    User user =
        new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
    User savedUser = userRepository.save(user);
    return "Bearer " + jwtService.generateToken(savedUser);
  }

  @Test
  void createInventory_requiresAdmin() throws Exception {
    CreateInventoryRequest request =
        new CreateInventoryRequest(hotel.getId(), RoomType.SINGLE, LocalDate.now().plusDays(1), 5);

    mockMvc
        .perform(
            post("/api/v1/inventory")
                .header("Authorization", bearerTokenFor(Role.CUSTOMER, "inventory-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void createInventory_withAdmin_returnsCreated() throws Exception {
    CreateInventoryRequest request =
        new CreateInventoryRequest(hotel.getId(), RoomType.SINGLE, LocalDate.now().plusDays(1), 5);

    mockMvc
        .perform(
            post("/api/v1/inventory")
                .header("Authorization", bearerTokenFor(Role.ADMIN, "inventory-admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
        .andExpect(jsonPath("$.roomType").value("SINGLE"));
  }

  @Test
  void getInventory_isPublic() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory")
                .param("hotelId", hotel.getId().toString())
                .param("from", LocalDate.now().plusDays(1).toString())
                .param("to", LocalDate.now().plusDays(2).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
