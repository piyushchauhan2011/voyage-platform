package com.voyage.app.kafka;

import com.voyage.app.hotel.Hotel;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
    name = "application.kafka.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HotelEventPublisher {

  private static final int HOTEL_EVENT_SCHEMA_VERSION = 1;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final Clock clock;
  private final String hotelEventsTopic;
  private final ObjectMapper objectMapper;

  public HotelEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      Clock clock,
      ObjectMapper objectMapper,
      @Value("${application.kafka.topic.hotel-events}") String hotelEventsTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.hotelEventsTopic = hotelEventsTopic;
  }

  public void publish(HotelEventType eventType, Hotel hotel) {
    HotelEvent event =
        new HotelEvent(
            UUID.randomUUID().toString(),
            HOTEL_EVENT_SCHEMA_VERSION,
            eventType,
            hotel.getId(),
            hotel.getName(),
            hotel.getCity(),
            hotel.getPricePerNight(),
            Instant.now(clock));
    try {
      kafkaTemplate.send(
          hotelEventsTopic, hotel.getId().toString(), objectMapper.writeValueAsString(event));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize hotel event", exception);
    }
  }

  public void publishRaw(String messageKey, String payload) {
    kafkaTemplate.send(hotelEventsTopic, messageKey, payload);
  }
}
