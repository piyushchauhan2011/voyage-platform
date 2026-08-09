package com.voyage.app.jobs;

import java.time.Instant;

public record DelayedJob(
    String jobId,
    String routingKey,
    String payload,
    JobSource source,
    Instant createdAt,
    Instant executeAt) {}
