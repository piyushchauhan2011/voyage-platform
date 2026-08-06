package com.voyage.app.ui;

public record PublishRawKafkaMessageResponse(String topicName, String messageKey, String payload) {
}