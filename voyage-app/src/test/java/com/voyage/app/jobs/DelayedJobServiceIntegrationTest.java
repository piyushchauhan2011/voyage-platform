package com.voyage.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.voyage.app.redis.RedisIntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.jobs.enabled=true",
      "application.jobs.async-notifications=false",
      "application.jobs.poll-interval-ms=60000"
    })
class DelayedJobServiceIntegrationTest extends RedisIntegrationTestSupport {

  @DynamicPropertySource
  static void jobsRedis(DynamicPropertyRegistry registry) {
    registry.add("application.jobs.enabled", () -> true);
  }

  @Autowired DelayedJobService delayedJobService;

  @Test
  void scheduleListAndClaimDue() {
    DelayedJob future =
        delayedJobService.schedule("email.send", "later", Duration.ofMinutes(5), JobSource.DELAYED);
    DelayedJob due =
        delayedJobService.schedule("email.send", "now", Duration.ZERO, JobSource.DELAYED);

    List<DelayedJob> pending = delayedJobService.listPending();
    assertThat(pending).extracting(DelayedJob::jobId).contains(future.jobId(), due.jobId());

    List<DelayedJob> claimed = delayedJobService.claimDue(Instant.now().plusSeconds(1));
    assertThat(claimed).extracting(DelayedJob::jobId).contains(due.jobId());
    assertThat(claimed).extracting(DelayedJob::jobId).doesNotContain(future.jobId());

    assertThat(delayedJobService.listPending())
        .extracting(DelayedJob::jobId)
        .containsExactly(future.jobId());
  }

  @Test
  void purgeClearsDelayedKey() {
    delayedJobService.schedule("booking.confirm", "x", Duration.ofSeconds(30), JobSource.DELAYED);
    assertThat(delayedJobService.pendingCount()).isGreaterThan(0);
    assertThat(delayedJobService.purge()).isGreaterThan(0);
    assertThat(delayedJobService.pendingCount()).isZero();
  }
}
