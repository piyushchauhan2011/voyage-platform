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
        String errorClassName,
        String errorMessage,
        Instant deadLetteredAt
) {
}