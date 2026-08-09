package com.voyage.app.seed;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voyage.app.security.JwtService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.seed.enabled=true",
      "application.seed.on-startup-if-empty=false",
      "application.seed.hotels=2",
      "application.seed.inventory-days=1",
      "application.seed.customers=1"
    })
class SeedControllerSecurityTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;

  private String adminToken;
  private String customerToken;

  @BeforeEach
  void setUp() {
    User admin =
        userRepository
            .findByUsername("seed-admin")
            .orElseGet(
                () ->
                    userRepository.save(
                        new User(
                            "seed-admin",
                            "seed-admin@test.com",
                            passwordEncoder.encode("password123"),
                            Role.ADMIN)));
    User customer =
        userRepository
            .findByUsername("seed-customer")
            .orElseGet(
                () ->
                    userRepository.save(
                        new User(
                            "seed-customer",
                            "seed-customer@test.com",
                            passwordEncoder.encode("password123"),
                            Role.CUSTOMER)));
    adminToken = "Bearer " + jwtService.generateToken(admin);
    customerToken = "Bearer " + jwtService.generateToken(customer);
  }

  @Test
  void seedDemo_requiresAdmin() throws Exception {
    mockMvc
        .perform(post("/api/seed/demo").header("Authorization", customerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void seedDemo_asAdmin_returnsCounts() throws Exception {
    mockMvc
        .perform(post("/api/seed/demo").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hotelsCreated").value(2))
        .andExpect(jsonPath("$.adminUsername").value("admin"))
        .andExpect(jsonPath("$.defaultPassword").value("password123"));
  }
}
