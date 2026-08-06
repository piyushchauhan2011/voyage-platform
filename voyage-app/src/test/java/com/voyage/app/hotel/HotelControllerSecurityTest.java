package com.voyage.app.hotel;

import com.voyage.app.security.JwtService;
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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demonstrates the three security outcomes:
 *   401 Unauthorized  — no/invalid token (unauthenticated)
 *   403 Forbidden     — valid token but wrong role (authenticated, not authorized)
 *   2xx              — valid token with required role
 *
 * @WithMockUser bypasses the JWT filter and sets a pre-built Authentication directly
 * in SecurityContextHolder — useful for testing authorization rules without real tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HotelControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String bearerTokenFor(Role role, String username) {
        User user = new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
        User savedUser = userRepository.save(user);
        return "Bearer " + jwtService.generateToken(savedUser);
    }

    @Test
    void getHotels_withoutToken_returns200() throws Exception {
        // GET /api/hotels is public — no token required
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk());
    }

    @Test
    void createHotel_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Test Hotel", "Paris", 150.0))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createHotel_withUserRole_returns403() throws Exception {
        // Authenticated but ROLE_USER cannot write hotels — only ROLE_ADMIN can
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", bearerTokenFor(Role.USER, "hotel-user-create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Test Hotel", "Paris", 150.0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createHotel_withAdminRole_returns201() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "hotel-admin-create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Hotel("Grand Hyatt", "Tokyo", 320.0))))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteHotel_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/hotels/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteHotel_nonExistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/hotels/999999")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "hotel-admin-delete-missing")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHotel_withUserRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/hotels/1")
                        .header("Authorization", bearerTokenFor(Role.USER, "hotel-user-delete")))
                .andExpect(status().isForbidden());
    }
}
