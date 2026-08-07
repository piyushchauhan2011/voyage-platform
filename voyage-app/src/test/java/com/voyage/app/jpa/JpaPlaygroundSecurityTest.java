package com.voyage.app.jpa;

import com.voyage.app.security.JwtService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JpaPlaygroundSecurityTest {

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
        mockMvc.perform(post("/api/jpa/playground/seed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seed_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/api/jpa/playground/seed")
                        .header("Authorization", bearerTokenFor(Role.USER, "jpa-lab-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void seed_withAdminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/jpa/playground/seed")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "jpa-lab-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").isNumber())
                .andExpect(jsonPath("$.username").value(JpaPlaygroundService.LAB_USER));
    }

    @Test
    void nplus1_withAdmin_returnsQueryCount() throws Exception {
        String token = bearerTokenFor(Role.ADMIN, "jpa-lab-nplus1-admin");
        mockMvc.perform(post("/api/jpa/playground/seed")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/jpa/playground/loading/nplus1")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("nplus1"))
                .andExpect(jsonPath("$.queryCount").isNumber())
                .andExpect(jsonPath("$.bookingCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
