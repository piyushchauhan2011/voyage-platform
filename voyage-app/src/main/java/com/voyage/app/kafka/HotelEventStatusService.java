package com.voyage.app.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class HotelEventStatusService {

    private final ProcessedHotelEventRepository processedHotelEventRepository;
    private final Clock clock;
    private final String consumerGroupId;

    public HotelEventStatusService(ProcessedHotelEventRepository processedHotelEventRepository,
                                   Clock clock,
                                   @Value("${spring.kafka.consumer.group-id}") String consumerGroupId) {
        this.processedHotelEventRepository = processedHotelEventRepository;
        this.clock = clock;
        this.consumerGroupId = consumerGroupId;
    }

    @Transactional
    public void recordProcessedEvent(HotelEvent event,
                                     String topicName,
                                     int partitionId,
                                     long kafkaOffset,
                                     String messageKey) {
        if (processedHotelEventRepository.existsByEventId(event.eventId())) {
            return;
        }

        ProcessedHotelEvent processedEvent = new ProcessedHotelEvent();
        processedEvent.setEventId(event.eventId());
        processedEvent.setSchemaVersion(event.schemaVersion());
        processedEvent.setEventType(event.eventType());
        processedEvent.setHotelId(event.hotelId());
        processedEvent.setHotelName(event.hotelName());
        processedEvent.setCity(event.city());
        processedEvent.setPricePerNight(event.pricePerNight());
        processedEvent.setTopicName(topicName);
        processedEvent.setMessageKey(messageKey);
        processedEvent.setPartitionId(partitionId);
        processedEvent.setKafkaOffset(kafkaOffset);
        processedEvent.setConsumerGroupId(consumerGroupId);
        processedEvent.setOccurredAt(event.occurredAt());
        processedEvent.setProcessedAt(Instant.now(clock));

        try {
            processedHotelEventRepository.save(processedEvent);
        } catch (DataIntegrityViolationException ignored) {
            // Duplicate delivery is acceptable in at-least-once systems; the unique event id keeps storage idempotent.
        }
    }

    @Transactional(readOnly = true)
    public List<ProcessedHotelEvent> getRecentEvents() {
        return processedHotelEventRepository.findTop20ByOrderByProcessedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ProcessedHotelEvent> getEventHistory() {
        return processedHotelEventRepository.findAll(Sort.by(Sort.Direction.DESC, "processedAt"));
    }
}