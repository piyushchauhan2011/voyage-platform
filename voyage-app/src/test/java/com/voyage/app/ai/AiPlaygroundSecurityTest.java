package com.voyage.app.ai;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The AI playground exposes an outbound paid API and can write to the vector store,
 * so it sits behind ADMIN like the other lab playgrounds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiPlaygroundSecurityTest {

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
    void assistant_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/ai/playground/assistant"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assistant_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/api/ai/playground/assistant")
                        .header("Authorization", bearerTokenFor(Role.USER, "ai-lab-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/playground/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/api/ai/playground/ingest")
                        .header("Authorization", bearerTokenFor(Role.USER, "ai-lab-ingest-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void labPageStaysPublic() throws Exception {
        mockMvc.perform(get("/ui/ai"))
                .andExpect(status().isOk());
    }
}
