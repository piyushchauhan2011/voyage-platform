package com.voyage.app.redis;

import com.voyage.app.VoyageAppApplication;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = VoyageAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisPlaygroundIntegrationTest extends RedisIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String adminToken;

    @BeforeEach
    void setUpAdminToken() {
        userRepository.deleteAll();
        User user = new User("redis-admin", "redis-admin@test.com", passwordEncoder.encode("password123"), Role.ADMIN);
        adminToken = "Bearer " + jwtService.generateToken(userRepository.save(user));
    }

    @Test
    void stringAndTtlEndpoints_roundTrip() throws Exception {
        mockMvc.perform(post("/api/redis/playground/strings/demo:test")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedisStringWriteRequest("available", 90L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("demo:test"))
                .andExpect(jsonPath("$.value").value("available"));

        mockMvc.perform(get("/api/redis/playground/ttl/demo:test")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ttlSecondsRemaining").isNumber());
    }

    @Test
    void pubSubAndLockEndpoints_workForAdminUsers() throws Exception {
        String pubSubMessage = "booking-created-integration";

        mockMvc.perform(post("/api/redis/playground/pubsub/publish")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedisPublishRequest(pubSubMessage))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(pubSubMessage));

        boolean messageObserved = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            String response = mockMvc.perform(get("/api/redis/playground/pubsub/messages")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            if (objectMapper.readTree(response).toString().contains(pubSubMessage)) {
                messageObserved = true;
                break;
            }

            Thread.sleep(100);
        }

        assert messageObserved;

        mockMvc.perform(post("/api/redis/playground/locks/inventory:hotel-42/acquire")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedisLockAcquireRequest("owner-1", 30L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acquired").value(true));

        mockMvc.perform(post("/api/redis/playground/locks/inventory:hotel-42/acquire")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedisLockAcquireRequest("owner-2", 30L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acquired").value(false));

        mockMvc.perform(delete("/api/redis/playground/locks/inventory:hotel-42")
                        .header("Authorization", adminToken)
                        .param("owner", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true));
    }
}