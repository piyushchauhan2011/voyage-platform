package com.voyage.app.kafka;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeadLetterHotelEventService {

  private final DeadLetterHotelEventRepository deadLetterHotelEventRepository;
  private final KafkaTemplate<String, String> hotelEventKafkaTemplate;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public DeadLetterHotelEventService(
      DeadLetterHotelEventRepository deadLetterHotelEventRepository,
      ObjectProvider<KafkaTemplate<String, String>> hotelEventKafkaTemplateProvider,
      Clock clock,
      ObjectMapper objectMapper) {
    this.deadLetterHotelEventRepository = deadLetterHotelEventRepository;
    this.hotelEventKafkaTemplate = hotelEventKafkaTemplateProvider.getIfAvailable();
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void recordDeadLetter(
      String originalTopic,
      String deadLetterTopic,
      String messageKey,
      int partitionId,
      long kafkaOffset,
      String payload,
      String originalEventId,
      HotelEventType originalEventType,
      Long originalHotelId,
      String errorClassName,
      String errorMessage) {
    DeadLetterHotelEvent event = new DeadLetterHotelEvent();
    event.setOriginalTopic(originalTopic);
    event.setDeadLetterTopic(deadLetterTopic);
    event.setMessageKey(messageKey);
    event.setPartitionId(partitionId);
    event.setKafkaOffset(kafkaOffset);
    event.setPayload(payload);
    event.setOriginalEventId(originalEventId);
    event.setOriginalEventType(originalEventType);
    event.setOriginalHotelId(originalHotelId);
    event.setErrorClassName(errorClassName);
    event.setErrorMessage(errorMessage);
    event.setRetryStatus(DeadLetterRetryStatus.PENDING);
    event.setRetryCount(0);
    event.setDeadLetteredAt(Instant.now(clock));
    deadLetterHotelEventRepository.save(event);
  }

  @Transactional(readOnly = true)
  public List<DeadLetterHotelEvent> getRecentDeadLetters() {
    return deadLetterHotelEventRepository.findTop20ByOrderByDeadLetteredAtDesc();
  }

  @Transactional(readOnly = true)
  public List<DeadLetterHotelEvent> getDeadLetterHistory() {
    return deadLetterHotelEventRepository.findAll(Sort.by(Sort.Direction.DESC, "deadLetteredAt"));
  }

  @Transactional
  public DeadLetterHotelEvent retryDeadLetter(Long id, String payloadOverride) {
    DeadLetterHotelEvent event =
        deadLetterHotelEventRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Dead-letter event not found: " + id));

    String payloadToReplay =
        StringUtils.hasText(payloadOverride) ? payloadOverride : event.getPayload();
    event.setPayload(payloadToReplay);
    event.setRetryCount(event.getRetryCount() + 1);
    event.setLastRetriedAt(Instant.now(clock));
    event.setRetryStatus(DeadLetterRetryStatus.RETRIED);
    event.setResolvedAt(null);

    refreshDerivedFields(event, payloadToReplay);

    if (hotelEventKafkaTemplate == null) {
      throw new IllegalStateException("Kafka replay is disabled for this profile");
    }

    hotelEventKafkaTemplate.send(event.getOriginalTopic(), event.getMessageKey(), payloadToReplay);
    return deadLetterHotelEventRepository.save(event);
  }

  @Transactional
  public void markResolvedForEventId(String originalEventId) {
    if (!StringUtils.hasText(originalEventId)) {
      return;
    }

    Instant resolvedAt = Instant.now(clock);
    for (DeadLetterHotelEvent event :
        deadLetterHotelEventRepository.findByOriginalEventIdOrderByDeadLetteredAtDesc(
            originalEventId)) {
      event.setRetryStatus(DeadLetterRetryStatus.RESOLVED);
      event.setResolvedAt(resolvedAt);
    }
  }

  private void refreshDerivedFields(DeadLetterHotelEvent event, String payload) {
    try {
      HotelEvent hotelEvent = objectMapper.readValue(payload, HotelEvent.class);
      event.setOriginalEventId(hotelEvent.eventId());
      event.setOriginalEventType(hotelEvent.eventType());
      event.setOriginalHotelId(hotelEvent.hotelId());
    } catch (Exception ignored) {
      event.setOriginalEventId(null);
      event.setOriginalEventType(null);
      event.setOriginalHotelId(null);
    }
  }
}
