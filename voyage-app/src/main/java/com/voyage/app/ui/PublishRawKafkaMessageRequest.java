package com.voyage.app.ui;

public record PublishRawKafkaMessageRequest(String messageKey, String payload) {
}