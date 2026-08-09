package com.voyage.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voyage.app.rabbitmq.LabJobMessage;
import com.voyage.app.redis.RedisIntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.jobs.enabled=true",
      "application.jobs.async-notifications=false",
      "application.jobs.poll-interval-ms=60000"
    })
class DelayedJobDispatcherIntegrationTest extends RedisIntegrationTestSupport {

  @Autowired DelayedJobService delayedJobService;
  @Autowired DelayedJobDispatcher delayedJobDispatcher;
  @MockitoBean DomainJobPublisher domainJobPublisher;

  @Test
  void dispatchPublishesOnlyDueJobs() {
    DelayedJob due =
        delayedJobService.schedule("email.send", "due-now", Duration.ZERO, JobSource.DELAYED);
    delayedJobService.schedule("email.send", "later", Duration.ofHours(1), JobSource.DELAYED);

    when(domainJobPublisher.publishDelayedJob(any(DelayedJob.class)))
        .thenAnswer(
            invocation -> {
              DelayedJob job = invocation.getArgument(0);
              return new LabJobMessage(job.jobId(), job.routingKey(), job.payload(), Instant.now());
            });

    delayedJobDispatcher.dispatchDueJobs();

    ArgumentCaptor<DelayedJob> captor = ArgumentCaptor.forClass(DelayedJob.class);
    verify(domainJobPublisher, times(1)).publishDelayedJob(captor.capture());
    assertThat(captor.getValue().jobId()).isEqualTo(due.jobId());
    assertThat(delayedJobService.listPending()).hasSize(1);
    assertThat(delayedJobService.listPending().getFirst().payload()).isEqualTo("later");
  }
}
