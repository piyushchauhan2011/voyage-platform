package com.voyage.app.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "processed_hotel_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_hotel_events_event_id", columnNames = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessedHotelEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private HotelEventType eventType;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "hotel_name", nullable = false)
    private String hotelName;

    @Column(nullable = false)
    private String city;

    @Column(name = "price_per_night", nullable = false)
    private Double pricePerNight;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;

    @Column(name = "kafka_offset", nullable = false)
    private Long kafkaOffset;

    @Column(name = "consumer_group_id", nullable = false)
    private String consumerGroupId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}