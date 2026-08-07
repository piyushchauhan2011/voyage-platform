package com.voyage.app.postgres;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgresPlaygroundSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String bearerTokenFor(Role role, String username) {
        User user = new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
        User savedUser = userRepository.save(user);
        return "Bearer " + jwtService.generateToken(savedUser);
    }

    @Test
    void seed_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/postgres/playground/seed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seed_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/api/postgres/playground/seed")
                        .header("Authorization", bearerTokenFor(Role.CUSTOMER, "pg-lab-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void seed_withAdminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/postgres/playground/seed")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "pg-lab-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoHotelId").isNumber())
                .andExpect(jsonPath("$.hotelCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(50)));
    }

    @Test
    void explain_withAdmin_returnsPlan() throws Exception {
        String token = bearerTokenFor(Role.ADMIN, "pg-lab-explain-admin");
        mockMvc.perform(post("/api/postgres/playground/seed")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/postgres/playground/explain")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"booking_by_hotel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("booking_by_hotel"))
                .andExpect(jsonPath("$.plan").isString())
                .andExpect(jsonPath("$.sql").value(org.hamcrest.Matchers.containsString("hotel_id")));
    }
}
