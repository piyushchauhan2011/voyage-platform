package com.voyage.app.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "application.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterHotelEventListener {

    private final DeadLetterHotelEventService deadLetterHotelEventService;

    public DeadLetterHotelEventListener(DeadLetterHotelEventService deadLetterHotelEventService) {
        this.deadLetterHotelEventService = deadLetterHotelEventService;
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
        deadLetterHotelEventService.recordDeadLetter(
                originalTopic != null ? originalTopic : "unknown",
                deadLetterTopic,
                messageKey,
                partitionId,
                kafkaOffset,
                payload,
                errorClassName,
                errorMessage
        );
    }
}