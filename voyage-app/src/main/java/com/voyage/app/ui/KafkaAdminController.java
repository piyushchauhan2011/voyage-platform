package com.voyage.app.ui;

import com.voyage.app.kafka.DeadLetterHotelEvent;
import com.voyage.app.kafka.DeadLetterHotelEventService;
import com.voyage.app.kafka.HotelEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kafka")
@ConditionalOnProperty(
    name = "application.kafka.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaAdminController {

  private final DeadLetterHotelEventService deadLetterHotelEventService;
  private final HotelEventPublisher hotelEventPublisher;
  private final String hotelEventsTopic;

  public KafkaAdminController(
      DeadLetterHotelEventService deadLetterHotelEventService,
      HotelEventPublisher hotelEventPublisher,
      @Value("${application.kafka.topic.hotel-events}") String hotelEventsTopic) {
    this.deadLetterHotelEventService = deadLetterHotelEventService;
    this.hotelEventPublisher = hotelEventPublisher;
    this.hotelEventsTopic = hotelEventsTopic;
  }

  @PostMapping("/publish-raw")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public PublishRawKafkaMessageResponse publishRaw(
      @RequestBody PublishRawKafkaMessageRequest request) {
    hotelEventPublisher.publishRaw(request.messageKey(), request.payload());
    return new PublishRawKafkaMessageResponse(
        hotelEventsTopic, request.messageKey(), request.payload());
  }

  @PostMapping("/dead-letters/{id}/retry")
  @ResponseStatus(HttpStatus.OK)
  public DeadLetterRetryResponse retryDeadLetter(
      @PathVariable Long id, @RequestBody(required = false) RetryDeadLetterRequest request) {
    DeadLetterHotelEvent event =
        deadLetterHotelEventService.retryDeadLetter(
            id, request != null ? request.payloadOverride() : null);
    return new DeadLetterRetryResponse(
        event.getId(),
        event.getRetryStatus().name(),
        event.getRetryCount(),
        event.getLastRetriedAt(),
        event.getOriginalTopic());
  }
}
