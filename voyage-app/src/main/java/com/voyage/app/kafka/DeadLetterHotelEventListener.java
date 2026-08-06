package com.voyage.app.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "application.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterHotelEventListener {

    private final DeadLetterHotelEventService deadLetterHotelEventService;
    private final ObjectMapper objectMapper;

    public DeadLetterHotelEventListener(DeadLetterHotelEventService deadLetterHotelEventService,
                                        ObjectMapper objectMapper) {
        this.deadLetterHotelEventService = deadLetterHotelEventService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${application.kafka.topic.hotel-events-dlt}")
    public void consumeDeadLetter(String payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String deadLetterTopic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partitionId,
                                  @Header(KafkaHeaders.OFFSET) long kafkaOffset,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String messageKey,
                                  @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
                                  @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String errorClassName,
                                  @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String errorMessage) {
        HotelEvent hotelEvent = tryParse(payload);
        deadLetterHotelEventService.recordDeadLetter(
                originalTopic != null ? originalTopic : "unknown",
                deadLetterTopic,
                messageKey,
                partitionId,
                kafkaOffset,
                payload,
                hotelEvent != null ? hotelEvent.eventId() : null,
                hotelEvent != null ? hotelEvent.eventType() : null,
                hotelEvent != null ? hotelEvent.hotelId() : null,
                errorClassName,
                errorMessage
        );
    }

    private HotelEvent tryParse(String payload) {
        try {
            return objectMapper.readValue(payload, HotelEvent.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}