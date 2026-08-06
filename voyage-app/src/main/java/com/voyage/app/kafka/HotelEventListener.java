package com.voyage.app.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableKafka
@ConditionalOnProperty(name = "application.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class HotelEventListener {

    private final HotelEventStatusService hotelEventStatusService;
    private final ObjectMapper objectMapper;

    public HotelEventListener(HotelEventStatusService hotelEventStatusService,
                              ObjectMapper objectMapper) {
        this.hotelEventStatusService = hotelEventStatusService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${application.kafka.topic.hotel-events}")
    public void consume(String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topicName,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partitionId,
                        @Header(KafkaHeaders.OFFSET) long kafkaOffset,
                        @Header(KafkaHeaders.RECEIVED_KEY) String messageKey) {
        try {
            HotelEvent event = objectMapper.readValue(payload, HotelEvent.class);
            hotelEventStatusService.recordProcessedEvent(event, topicName, partitionId, kafkaOffset, messageKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize hotel event", exception);
        }
    }
}