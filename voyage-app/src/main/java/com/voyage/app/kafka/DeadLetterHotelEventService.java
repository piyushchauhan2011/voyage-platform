package com.voyage.app.kafka;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class DeadLetterHotelEventService {

    private final DeadLetterHotelEventRepository deadLetterHotelEventRepository;
    private final Clock clock;

    public DeadLetterHotelEventService(DeadLetterHotelEventRepository deadLetterHotelEventRepository,
                                       Clock clock) {
        this.deadLetterHotelEventRepository = deadLetterHotelEventRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordDeadLetter(String originalTopic,
                                 String deadLetterTopic,
                                 String messageKey,
                                 int partitionId,
                                 long kafkaOffset,
                                 String payload,
                                 String errorClassName,
                                 String errorMessage) {
        DeadLetterHotelEvent event = new DeadLetterHotelEvent();
        event.setOriginalTopic(originalTopic);
        event.setDeadLetterTopic(deadLetterTopic);
        event.setMessageKey(messageKey);
        event.setPartitionId(partitionId);
        event.setKafkaOffset(kafkaOffset);
        event.setPayload(payload);
        event.setErrorClassName(errorClassName);
        event.setErrorMessage(errorMessage);
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
}