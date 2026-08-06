package com.voyage.app.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "application.kafka.enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=voyage-kafka-dlt-it",
        "application.kafka.topic.hotel-events=hotel-events-dlt-it",
        "application.kafka.topic.hotel-events-dlt=hotel-events-dlt-it.DLT",
        "application.kafka.retry.attempts=2",
        "application.kafka.retry.backoff-ms=50"
})
@EmbeddedKafka(partitions = 1, topics = {"hotel-events-dlt-it", "hotel-events-dlt-it.DLT"})
@ActiveProfiles("test")
@DirtiesContext
class HotelEventDeadLetterFlowTest {

    @Autowired KafkaTemplate<String, String> hotelEventKafkaTemplate;
    @Autowired DeadLetterHotelEventRepository deadLetterHotelEventRepository;

    @Test
    void malformedPayload_isRetriedThenPersistedFromDeadLetterTopic() throws Exception {
        hotelEventKafkaTemplate.send("hotel-events-dlt-it", "broken-key", "{not-json}").get();

        DeadLetterHotelEvent deadLetterEvent = awaitDeadLetterEvent();

        assertNotNull(deadLetterEvent);
        assertEquals("hotel-events-dlt-it", deadLetterEvent.getOriginalTopic());
        assertEquals("hotel-events-dlt-it.DLT", deadLetterEvent.getDeadLetterTopic());
        assertEquals("broken-key", deadLetterEvent.getMessageKey());
        assertEquals("{not-json}", deadLetterEvent.getPayload());
        assertTrue(deadLetterEvent.getErrorClassName() == null || !deadLetterEvent.getErrorClassName().isBlank());
        assertTrue(deadLetterEvent.getKafkaOffset() >= 0);
    }

    private DeadLetterHotelEvent awaitDeadLetterEvent() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));

        while (Instant.now().isBefore(deadline)) {
            if (!deadLetterHotelEventRepository.findTop20ByOrderByDeadLetteredAtDesc().isEmpty()) {
                return deadLetterHotelEventRepository.findTop20ByOrderByDeadLetteredAtDesc().getFirst();
            }
            Thread.sleep(200);
        }

        throw new AssertionError("Timed out waiting for dead-letter hotel event");
    }
}