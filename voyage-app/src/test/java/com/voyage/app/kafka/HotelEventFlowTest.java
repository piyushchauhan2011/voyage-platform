package com.voyage.app.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelService;
import com.voyage.app.token.RefreshTokenRepository;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "application.kafka.enabled=true",
      "spring.kafka.listener.auto-startup=true",
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.consumer.group-id=voyage-kafka-it",
      "application.kafka.topic.hotel-events=hotel-events-it"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {"hotel-events-it"})
@ActiveProfiles("test")
@DirtiesContext
class HotelEventFlowTest {

  @Autowired HotelService hotelService;
  @Autowired ProcessedHotelEventRepository processedHotelEventRepository;
  @Autowired UserRepository userRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUpAdminSecurityContext() {
    refreshTokenRepository.deleteAll();
    userRepository.deleteAll();
    User admin =
        userRepository.save(
            new User(
                "kafka-admin",
                "kafka-admin@test.com",
                passwordEncoder.encode("password123"),
                Role.ADMIN));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                admin.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()))));
  }

  @Test
  void save_publishesAndConsumesHotelCreatedEvent() throws Exception {
    Hotel savedHotel = hotelService.save(new Hotel("Kafka Test Hotel", "Berlin", 199.0));

    ProcessedHotelEvent processedEvent =
        awaitProcessedEvent(savedHotel.getId(), HotelEventType.CREATED);

    assertNotNull(processedEvent);
    assertEquals("Kafka Test Hotel", processedEvent.getHotelName());
    assertEquals("Berlin", processedEvent.getCity());
    assertEquals(savedHotel.getId(), processedEvent.getHotelId());
    assertEquals("hotel-events-it", processedEvent.getTopicName());
    assertEquals(savedHotel.getId().toString(), processedEvent.getMessageKey());
    assertEquals(0, processedEvent.getPartitionId());
    assertTrue(processedEvent.getKafkaOffset() >= 0);
    assertEquals("voyage-kafka-it", processedEvent.getConsumerGroupId());
  }

  private ProcessedHotelEvent awaitProcessedEvent(Long hotelId, HotelEventType eventType)
      throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));

    while (Instant.now().isBefore(deadline)) {
      ProcessedHotelEvent processedEvent =
          processedHotelEventRepository
              .findFirstByHotelIdAndEventTypeOrderByProcessedAtDesc(hotelId, eventType)
              .orElse(null);

      if (processedEvent != null) {
        return processedEvent;
      }

      Thread.sleep(200);
    }

    throw new AssertionError("Timed out waiting for processed hotel event");
  }
}
