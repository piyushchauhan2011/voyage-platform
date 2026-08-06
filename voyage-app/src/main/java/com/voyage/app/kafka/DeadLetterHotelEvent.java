package com.voyage.app.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dead_letter_hotel_events")
@Getter
@Setter
@NoArgsConstructor
public class DeadLetterHotelEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_topic", nullable = false)
    private String originalTopic;

    @Column(name = "dead_letter_topic", nullable = false)
    private String deadLetterTopic;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;

    @Column(name = "kafka_offset", nullable = false)
    private Long kafkaOffset;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(name = "original_event_id")
    private String originalEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_event_type")
    private HotelEventType originalEventType;

    @Column(name = "original_hotel_id")
    private Long originalHotelId;

    @Column(name = "error_class_name")
    private String errorClassName;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "retry_status", nullable = false)
    private DeadLetterRetryStatus retryStatus;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_retried_at")
    private Instant lastRetriedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "dead_lettered_at", nullable = false)
    private Instant deadLetteredAt;
}