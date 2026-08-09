package com.voyage.app.jobs;

import com.voyage.app.rabbitmq.LabJobMessage;
import com.voyage.app.rabbitmq.RabbitMqLabConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
    name = "application.rabbitmq.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DomainJobPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final String exchangeName;

  public DomainJobPublisher(
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${application.rabbitmq.exchange}") String exchangeName) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.exchangeName = exchangeName;
  }

  public LabJobMessage publishImmediate(String routingKey, String payload, JobSource source) {
    String body = enrichPayload(payload, source);
    LabJobMessage job =
        new LabJobMessage(
            UUID.randomUUID().toString(), routingKey.trim(), body, Instant.now(clock));
    rabbitTemplate.convertAndSend(exchangeName, routingKey.trim(), job);
    return job;
  }

  public LabJobMessage publishDelayedJob(DelayedJob delayedJob) {
    LabJobMessage job =
        new LabJobMessage(
            delayedJob.jobId(),
            delayedJob.routingKey(),
            enrichPayload(delayedJob.payload(), delayedJob.source()),
            Instant.now(clock));
    rabbitTemplate.convertAndSend(exchangeName, delayedJob.routingKey(), job);
    return job;
  }

  public LabJobMessage publishBookingNotification(DomainNotificationPayload payload) {
    String body;
    try {
      body = objectMapper.writeValueAsString(payload);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialise notification payload", ex);
    }
    LabJobMessage job =
        new LabJobMessage(
            UUID.randomUUID().toString(),
            RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND,
            body,
            Instant.now(clock));
    rabbitTemplate.convertAndSend(exchangeName, RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND, job);
    return job;
  }

  private String enrichPayload(String payload, JobSource source) {
    String body = payload == null || payload.isBlank() ? "lab-job" : payload.trim();
    if (body.startsWith("{")) {
      return body;
    }
    // Plain lab payloads: wrap so the worker can persist JobSource.
    try {
      return objectMapper.writeValueAsString(
          new LabEnvelope(source == null ? JobSource.IMMEDIATE : source, body));
    } catch (Exception ex) {
      return body;
    }
  }

  public record LabEnvelope(JobSource source, String text) {}
}
