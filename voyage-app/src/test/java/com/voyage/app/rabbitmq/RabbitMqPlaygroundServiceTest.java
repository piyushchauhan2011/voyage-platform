package com.voyage.app.rabbitmq;

import com.voyage.app.VoyageAppApplication;
import com.voyage.app.security.JwtService;
import com.voyage.app.token.RefreshTokenRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = VoyageAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RabbitMqPlaygroundServiceTest extends RabbitMqIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired LabJobRecorder labJobRecorder;

    private String adminToken;

    @BeforeEach
    void setUpAdminToken() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        labJobRecorder.clear();
        User user = new User("rmq-admin", "rmq-admin@test.com", passwordEncoder.encode("password123"), Role.ADMIN);
        adminToken = "Bearer " + jwtService.generateToken(userRepository.save(user));
    }

    @Test
    void setupPublishAndConsume_roundTrip() throws Exception {
        mockMvc.perform(post("/api/rabbitmq/playground/setup")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange").value(org.hamcrest.Matchers.startsWith("voyage.jobs.test.")))
                .andExpect(jsonPath("$.exchangeType").value("direct"))
                .andExpect(jsonPath("$.bindings.length()").value(2));

        MvcResult publishResult = mockMvc.perform(post("/api/rabbitmq/playground/publish")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routingKey\":\"booking.confirm\",\"payload\":\"integration-job\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routingKey").value("booking.confirm"))
                .andExpect(jsonPath("$.routedToKnownBinding").value(true))
                .andReturn();

        JsonNode publishJson = objectMapper.readTree(publishResult.getResponse().getContentAsString());
        String jobId = publishJson.get("jobId").asString();

        boolean observed = false;
        for (int attempt = 0; attempt < 40; attempt++) {
            String body = mockMvc.perform(get("/api/rabbitmq/playground/consumed")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains(jobId) && body.contains("booking.confirm")) {
                observed = true;
                break;
            }
            Thread.sleep(100);
        }
        assertThat(observed).as("expected consumer delivery for jobId=%s", jobId).isTrue();
    }

    @Test
    void routingDemo_andCompare_exposeEducationalFields() throws Exception {
        mockMvc.perform(post("/api/rabbitmq/playground/setup")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rabbitmq/playground/routing-demo")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedPublish.routingKey").value("booking.confirm"))
                .andExpect(jsonPath("$.matchedPublish.routedToKnownBinding").value(true))
                .andExpect(jsonPath("$.unmatchedPublish.routingKey").value("unknown.job"))
                .andExpect(jsonPath("$.unmatchedPublish.routedToKnownBinding").value(false))
                .andExpect(jsonPath("$.observation").isString());

        mockMvc.perform(get("/api/rabbitmq/playground/compare")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.rows[0].kafka").isString())
                .andExpect(jsonPath("$.rows[0].rabbitmq").isString());
    }
}
