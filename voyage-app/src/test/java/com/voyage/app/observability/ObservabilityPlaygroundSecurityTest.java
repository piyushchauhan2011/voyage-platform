package com.voyage.app.observability;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityPlaygroundSecurityTest {

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
    void cpuSpike_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/observability/playground/cpu-spike"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cpuSpike_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/api/observability/playground/cpu-spike")
                        .header("Authorization", bearerTokenFor(Role.USER, "obs-lab-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cpuSpike_withAdminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/observability/playground/cpu-spike")
                        .param("seconds", "1")
                        .param("threads", "1")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "obs-lab-cpu-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threads").value(1))
                .andExpect(jsonPath("$.seconds").value(1));
    }

    @Test
    void slowApi_withAdminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/observability/playground/slow-api")
                        .param("delayMs", "25")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "obs-lab-slow-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMs").value(25));
    }

    @Test
    void memoryLeak_andRelease_withAdmin_returns200() throws Exception {
        String token = bearerTokenFor(Role.ADMIN, "obs-lab-mem-admin");
        mockMvc.perform(post("/api/observability/playground/memory-leak")
                        .param("mb", "1")
                        .param("holdSeconds", "0")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mbAllocated").value(1));

        mockMvc.perform(delete("/api/observability/playground/memory-leak")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bytesReleased").value(1024 * 1024));
    }

    @Test
    void kafkaLag_whenKafkaDisabled_returns409() throws Exception {
        mockMvc.perform(post("/api/observability/playground/kafka-lag")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "obs-lab-kafka-admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Kafka")));
    }

    @Test
    void redisEviction_whenRedisDisabled_returns409() throws Exception {
        mockMvc.perform(post("/api/observability/playground/redis-eviction")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "obs-lab-redis-admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Redis")));
    }

    @Test
    void prometheusEndpoint_isPublicAndExposesMicrometerSeries() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("voyage_chaos_")))
                .andExpect(content().string(containsString("# TYPE")));
    }
}
