package com.voyage.app.rabbitmq;

import java.time.Instant;

public record LabJobDelivery(
    String jobId,
    String type,
    String payload,
    String exchange,
    String queue,
    String routingKey,
    Instant consumedAt) {}
