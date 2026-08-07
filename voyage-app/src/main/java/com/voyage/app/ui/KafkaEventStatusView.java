package com.voyage.app.ui;

import java.time.Instant;

public record KafkaEventStatusView(
    String eventId,
    Integer schemaVersion,
    String eventType,
    Long hotelId,
    String hotelName,
    String city,
    Double pricePerNight,
    String topicName,
    String messageKey,
    Integer partitionId,
    Long kafkaOffset,
    String consumerGroupId,
    Instant occurredAt,
    Instant processedAt) {}
