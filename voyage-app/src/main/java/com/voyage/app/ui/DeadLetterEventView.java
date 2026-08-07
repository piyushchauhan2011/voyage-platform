package com.voyage.app.ui;

import java.time.Instant;

public record DeadLetterEventView(
    Long id,
    String originalTopic,
    String deadLetterTopic,
    String messageKey,
    Integer partitionId,
    Long kafkaOffset,
    String payload,
    String originalEventId,
    String originalEventType,
    Long originalHotelId,
    String errorClassName,
    String errorMessage,
    String retryStatus,
    Integer retryCount,
    Instant lastRetriedAt,
    Instant resolvedAt,
    Instant deadLetteredAt) {}
