package com.voyage.app.rabbitmq;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RabbitMqPlaygroundSecurityTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;

  private String bearerTokenFor(Role role, String username) {
    User user =
        new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
    User savedUser = userRepository.save(user);
    return "Bearer " + jwtService.generateToken(savedUser);
  }

  @Test
  void setup_withoutToken_returns401() throws Exception {
    mockMvc.perform(post("/api/rabbitmq/playground/setup")).andExpect(status().isUnauthorized());
  }

  @Test
  void setup_withUserRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/rabbitmq/playground/setup")
                .header("Authorization", bearerTokenFor(Role.CUSTOMER, "rmq-lab-user")))
        .andExpect(status().isForbidden());
  }
}
