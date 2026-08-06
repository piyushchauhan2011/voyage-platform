package com.voyage.app.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "error_class_name")
    private String errorClassName;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "dead_lettered_at", nullable = false)
    private Instant deadLetteredAt;
}