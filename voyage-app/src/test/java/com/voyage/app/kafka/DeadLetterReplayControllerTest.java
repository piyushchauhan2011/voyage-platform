package com.voyage.app.kafka;

import com.voyage.app.security.JwtService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "application.kafka.enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=voyage-kafka-replay-it",
        "application.kafka.topic.hotel-events=hotel-events-replay-it",
        "application.kafka.topic.hotel-events-dlt=hotel-events-replay-it.DLT"
})
@EmbeddedKafka(partitions = 1, topics = {"hotel-events-replay-it", "hotel-events-replay-it.DLT"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext
class DeadLetterReplayControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DeadLetterHotelEventRepository deadLetterHotelEventRepository;
    @Autowired ProcessedHotelEventRepository processedHotelEventRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @Test
    void retryDeadLetter_withAdminToken_requeuesPayloadAndResolvesDeadLetter() throws Exception {
        HotelEvent event = new HotelEvent(
                "event-replay-1",
                1,
                HotelEventType.CREATED,
                777L,
                "Replay Hotel",
                "Lisbon",
                210.0,
                Instant.now()
        );

        DeadLetterHotelEvent deadLetterEvent = new DeadLetterHotelEvent();
        deadLetterEvent.setOriginalTopic("hotel-events-replay-it");
        deadLetterEvent.setDeadLetterTopic("hotel-events-replay-it.DLT");
        deadLetterEvent.setMessageKey("777");
        deadLetterEvent.setPartitionId(0);
        deadLetterEvent.setKafkaOffset(2L);
        deadLetterEvent.setPayload(objectMapper.writeValueAsString(event));
        deadLetterEvent.setOriginalEventId(event.eventId());
        deadLetterEvent.setOriginalEventType(event.eventType());
        deadLetterEvent.setOriginalHotelId(event.hotelId());
        deadLetterEvent.setErrorClassName("java.lang.IllegalStateException");
        deadLetterEvent.setErrorMessage("Transient downstream failure");
        deadLetterEvent.setRetryStatus(DeadLetterRetryStatus.PENDING);
        deadLetterEvent.setRetryCount(0);
        deadLetterEvent.setDeadLetteredAt(Instant.now());
        deadLetterEvent = deadLetterHotelEventRepository.save(deadLetterEvent);

        mockMvc.perform(post("/api/kafka/dead-letters/{id}/retry", deadLetterEvent.getId())
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "kafka-replay-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deadLetterEvent.getId()))
                .andExpect(jsonPath("$.retryStatus").value("RETRIED"))
                .andExpect(jsonPath("$.retryCount").value(1));

        ProcessedHotelEvent processedEvent = awaitProcessedEvent(event.eventId());
        DeadLetterHotelEvent reloadedDeadLetter = deadLetterHotelEventRepository.findById(deadLetterEvent.getId()).orElseThrow();

        assertNotNull(processedEvent);
        assertEquals(event.hotelId(), processedEvent.getHotelId());
        assertEquals(DeadLetterRetryStatus.RESOLVED, reloadedDeadLetter.getRetryStatus());
        assertEquals(1, reloadedDeadLetter.getRetryCount());
        assertNotNull(reloadedDeadLetter.getResolvedAt());
    }

    @Test
    void retryDeadLetter_withUserToken_returns403() throws Exception {
        DeadLetterHotelEvent deadLetterEvent = new DeadLetterHotelEvent();
        deadLetterEvent.setOriginalTopic("hotel-events-replay-it");
        deadLetterEvent.setDeadLetterTopic("hotel-events-replay-it.DLT");
        deadLetterEvent.setMessageKey("broken-key");
        deadLetterEvent.setPartitionId(0);
        deadLetterEvent.setKafkaOffset(1L);
        deadLetterEvent.setPayload("{not-json}");
        deadLetterEvent.setRetryStatus(DeadLetterRetryStatus.PENDING);
        deadLetterEvent.setRetryCount(0);
        deadLetterEvent.setDeadLetteredAt(Instant.now());
        deadLetterEvent = deadLetterHotelEventRepository.save(deadLetterEvent);

        mockMvc.perform(post("/api/kafka/dead-letters/{id}/retry", deadLetterEvent.getId())
                        .header("Authorization", bearerTokenFor(Role.USER, "kafka-replay-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private String bearerTokenFor(Role role, String username) {
        User user = new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
        User savedUser = userRepository.save(user);
        return "Bearer " + jwtService.generateToken(savedUser);
    }

    private ProcessedHotelEvent awaitProcessedEvent(String eventId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));

        while (Instant.now().isBefore(deadline)) {
            for (ProcessedHotelEvent processedEvent : processedHotelEventRepository.findTop20ByOrderByProcessedAtDesc()) {
                if (eventId.equals(processedEvent.getEventId())) {
                    return processedEvent;
                }
            }
            Thread.sleep(200);
        }

        throw new AssertionError("Timed out waiting for replayed processed event");
    }
}