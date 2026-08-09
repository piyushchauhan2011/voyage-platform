package com.voyage.app.jobs;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls Redis for due delayed jobs and publishes them to RabbitMQ (Laravel scheduler + queue
 * hand-off).
 */
@Component
@ConditionalOnProperty(
    name = "application.jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DelayedJobDispatcher {

  private static final Logger log = LoggerFactory.getLogger(DelayedJobDispatcher.class);

  private final ObjectProvider<DelayedJobService> delayedJobService;
  private final ObjectProvider<DomainJobPublisher> domainJobPublisher;
  private final SchedulerStatus schedulerStatus;
  private final Clock clock;

  public DelayedJobDispatcher(
      ObjectProvider<DelayedJobService> delayedJobService,
      ObjectProvider<DomainJobPublisher> domainJobPublisher,
      SchedulerStatus schedulerStatus,
      Clock clock) {
    this.delayedJobService = delayedJobService;
    this.domainJobPublisher = domainJobPublisher;
    this.schedulerStatus = schedulerStatus;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${application.jobs.poll-interval-ms:1000}")
  public void dispatchDueJobs() {
    DelayedJobService delay = delayedJobService.getIfAvailable();
    DomainJobPublisher publisher = domainJobPublisher.getIfAvailable();
    if (delay == null || publisher == null) {
      schedulerStatus.recordPoll(Instant.now(clock), 0);
      return;
    }

    Instant now = Instant.now(clock);
    List<DelayedJob> due = delay.claimDue(now);
    for (DelayedJob job : due) {
      try {
        publisher.publishDelayedJob(job);
      } catch (RuntimeException ex) {
        log.warn("Failed to publish delayed job {}: {}", job.jobId(), ex.getMessage());
      }
    }
    schedulerStatus.recordPoll(now, due.size());
  }
}
