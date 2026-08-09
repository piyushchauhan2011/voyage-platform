package com.voyage.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.jobs.enabled=true",
      "application.jobs.async-notifications=false",
      "application.jobs.history-retention=PT0S"
    })
class JobHistoryCleanupSchedulerTest {

  @Autowired JobRunService jobRunService;
  @Autowired JobHistoryCleanupScheduler jobHistoryCleanupScheduler;
  @Autowired SchedulerStatus schedulerStatus;

  @Test
  void cleanupDeletesOldRunsAndRecordsHeartbeat() {
    jobRunService.record(
        "job-1", "email.send", JobSource.IMMEDIATE, JobRunStatus.SUCCESS, "hello", null);
    assertThat(jobRunService.recent()).isNotEmpty();

    jobHistoryCleanupScheduler.cleanup();

    assertThat(schedulerStatus.lastCronAt()).isNotNull();
    assertThat(schedulerStatus.lastCleanupDeleted()).isGreaterThanOrEqualTo(1);
    assertThat(jobRunService.recent()).isEmpty();
  }
}
