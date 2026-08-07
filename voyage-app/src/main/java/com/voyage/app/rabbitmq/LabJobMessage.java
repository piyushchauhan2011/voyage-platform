package com.voyage.app.rabbitmq;

import java.time.Instant;

public record LabJobMessage(String jobId, String type, String payload, Instant createdAt) {}
