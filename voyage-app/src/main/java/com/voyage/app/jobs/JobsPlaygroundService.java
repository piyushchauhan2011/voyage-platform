package com.voyage.app.jobs;

import com.voyage.app.rabbitmq.LabJobMessage;
import com.voyage.app.rabbitmq.RabbitMqLabConfig;
import java.time.Duration;
import java.util.List;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "application.jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JobsPlaygroundService {

  private final ObjectProvider<DelayedJobService> delayedJobService;
  private final ObjectProvider<DomainJobPublisher> domainJobPublisher;
  private final ObjectProvider<AmqpAdmin> amqpAdmin;
  private final JobRunService jobRunService;
  private final SchedulerStatus schedulerStatus;
  private final String bookingConfirmQueue;
  private final String emailSendQueue;

  public JobsPlaygroundService(
      ObjectProvider<DelayedJobService> delayedJobService,
      ObjectProvider<DomainJobPublisher> domainJobPublisher,
      ObjectProvider<AmqpAdmin> amqpAdmin,
      JobRunService jobRunService,
      SchedulerStatus schedulerStatus,
      @Value("${application.rabbitmq.queues.booking-confirm}") String bookingConfirmQueue,
      @Value("${application.rabbitmq.queues.email-send}") String emailSendQueue) {
    this.delayedJobService = delayedJobService;
    this.domainJobPublisher = domainJobPublisher;
    this.amqpAdmin = amqpAdmin;
    this.jobRunService = jobRunService;
    this.schedulerStatus = schedulerStatus;
    this.bookingConfirmQueue = bookingConfirmQueue;
    this.emailSendQueue = emailSendQueue;
  }

  public LabJobMessage enqueueImmediate(String routingKey, String payload) {
    DomainJobPublisher publisher = requirePublisher();
    String key =
        routingKey == null || routingKey.isBlank()
            ? RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND
            : routingKey.trim();
    return publisher.publishImmediate(key, payload, JobSource.IMMEDIATE);
  }

  public DelayedJob enqueueDelayed(String routingKey, String payload, long delaySeconds) {
    DelayedJobService delay = requireDelay();
    String key =
        routingKey == null || routingKey.isBlank()
            ? RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND
            : routingKey.trim();
    return delay.schedule(
        key, payload, Duration.ofSeconds(Math.max(delaySeconds, 0)), JobSource.DELAYED);
  }

  public long purgeDelayed() {
    DelayedJobService delay = delayedJobService.getIfAvailable();
    return delay == null ? 0 : delay.purge();
  }

  public List<DelayedJob> delayedJobs() {
    DelayedJobService delay = delayedJobService.getIfAvailable();
    return delay == null ? List.of() : delay.listPending();
  }

  public List<JobRun> recentRuns() {
    return jobRunService.recent();
  }

  public SchedulerPanelView schedulerPanel() {
    DelayedJobService delay = delayedJobService.getIfAvailable();
    return new SchedulerPanelView(
        schedulerStatus.lastPollAt(),
        schedulerStatus.lastCronAt(),
        schedulerStatus.lastPollDispatched(),
        schedulerStatus.lastCleanupDeleted(),
        delay == null ? null : delay.delayedKey(),
        delay == null ? 0 : delay.pendingCount(),
        queueDepth(bookingConfirmQueue),
        queueDepth(emailSendQueue),
        delay != null,
        domainJobPublisher.getIfAvailable() != null);
  }

  private long queueDepth(String queueName) {
    AmqpAdmin admin = amqpAdmin.getIfAvailable();
    if (admin == null || domainJobPublisher.getIfAvailable() == null) {
      return -1;
    }
    try {
      QueueInformation info = admin.getQueueInfo(queueName);
      return info == null ? -1 : info.getMessageCount();
    } catch (RuntimeException ex) {
      return -1;
    }
  }

  private DomainJobPublisher requirePublisher() {
    DomainJobPublisher publisher = domainJobPublisher.getIfAvailable();
    if (publisher == null) {
      throw new IllegalStateException("RabbitMQ is disabled — enable application.rabbitmq.enabled");
    }
    return publisher;
  }

  private DelayedJobService requireDelay() {
    DelayedJobService delay = delayedJobService.getIfAvailable();
    if (delay == null) {
      throw new IllegalStateException("Redis is disabled — enable application.redis.enabled");
    }
    return delay;
  }

  public record SchedulerPanelView(
      java.time.Instant lastPollAt,
      java.time.Instant lastCronAt,
      int lastPollDispatched,
      int lastCleanupDeleted,
      String delayedKey,
      long delayedPending,
      long bookingConfirmDepth,
      long emailSendDepth,
      boolean redisEnabled,
      boolean rabbitEnabled) {}

  public record EnqueueImmediateRequest(String routingKey, String payload) {}

  public record EnqueueDelayedRequest(String routingKey, String payload, Long delaySeconds) {}
}
