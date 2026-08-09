package com.voyage.app.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cron-style cleanup of old {@link JobRun} rows (Laravel scheduler analogue). */
@Component
@ConditionalOnProperty(
    name = "application.jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JobHistoryCleanupScheduler {

  private final JobRunService jobRunService;
  private final SchedulerStatus schedulerStatus;
  private final Clock clock;
  private final Duration retention;

  public JobHistoryCleanupScheduler(
      JobRunService jobRunService,
      SchedulerStatus schedulerStatus,
      Clock clock,
      @Value("${application.jobs.history-retention:PT1H}") Duration retention) {
    this.jobRunService = jobRunService;
    this.schedulerStatus = schedulerStatus;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "${application.jobs.cleanup-cron:0 * * * * *}")
  public void cleanup() {
    Instant now = Instant.now(clock);
    int deleted = jobRunService.deleteOlderThan(now.minus(retention));
    schedulerStatus.recordCron(now, deleted);
  }
}
