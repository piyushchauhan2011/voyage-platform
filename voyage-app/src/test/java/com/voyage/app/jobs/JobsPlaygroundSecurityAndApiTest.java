package com.voyage.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voyage.app.redis.RedisIntegrationTestSupport;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.jobs.enabled=true",
      "application.jobs.async-notifications=false",
      "application.jobs.poll-interval-ms=60000"
    })
class JobsPlaygroundSecurityAndApiTest extends RedisIntegrationTestSupport {

  @DynamicPropertySource
  static void enableJobs(DynamicPropertyRegistry registry) {
    registry.add("application.jobs.enabled", () -> true);
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;
  @Autowired DelayedJobService delayedJobService;
  @MockitoBean DomainJobPublisher domainJobPublisher;

  private String adminToken;
  private String customerToken;

  @BeforeEach
  void setUp() {
    User admin =
        userRepository
            .findByUsername("jobs-lab-admin")
            .orElseGet(
                () ->
                    userRepository.save(
                        new User(
                            "jobs-lab-admin",
                            "jobs-lab-admin@test.com",
                            passwordEncoder.encode("password123"),
                            Role.ADMIN)));
    User customer =
        userRepository
            .findByUsername("jobs-lab-customer")
            .orElseGet(
                () ->
                    userRepository.save(
                        new User(
                            "jobs-lab-customer",
                            "jobs-lab-customer@test.com",
                            passwordEncoder.encode("password123"),
                            Role.CUSTOMER)));
    adminToken = "Bearer " + jwtService.generateToken(admin);
    customerToken = "Bearer " + jwtService.generateToken(customer);
  }

  @Test
  void enqueueDelayed_requiresAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/jobs/playground/enqueue-delayed")
                .header("Authorization", customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new JobsPlaygroundService.EnqueueDelayedRequest("email.send", "nope", 5L))))
        .andExpect(status().isForbidden());
  }

  @Test
  void enqueueDelayed_asAdmin_storesInRedis() throws Exception {
    mockMvc
        .perform(
            post("/api/jobs/playground/enqueue-delayed")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new JobsPlaygroundService.EnqueueDelayedRequest(
                            "email.send", "admin-delayed", 30L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.routingKey").value("email.send"))
        .andExpect(jsonPath("$.payload").value("admin-delayed"));

    assertThat(delayedJobService.pendingCount()).isEqualTo(1);

    mockMvc
        .perform(get("/api/jobs/playground/delayed").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].payload").value("admin-delayed"));

    mockMvc
        .perform(delete("/api/jobs/playground/delayed").header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purged").value(1));
  }
}
