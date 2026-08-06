package com.voyage.app.ui;

import java.time.Instant;

public record DeadLetterRetryResponse(
        Long id,
        String retryStatus,
        Integer retryCount,
        Instant lastRetriedAt,
        String originalTopic
) {
}