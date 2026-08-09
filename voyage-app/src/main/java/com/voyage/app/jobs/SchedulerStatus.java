package com.voyage.app.jobs;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** In-memory heartbeats for the Jobs Lab scheduler panel. */
@Component
public class SchedulerStatus {

  private final AtomicReference<Instant> lastPollAt = new AtomicReference<>();
  private final AtomicReference<Instant> lastCronAt = new AtomicReference<>();
  private final AtomicInteger lastPollDispatched = new AtomicInteger();
  private final AtomicInteger lastCleanupDeleted = new AtomicInteger();

  public void recordPoll(Instant when, int dispatched) {
    lastPollAt.set(when);
    lastPollDispatched.set(dispatched);
  }

  public void recordCron(Instant when, int deleted) {
    lastCronAt.set(when);
    lastCleanupDeleted.set(deleted);
  }

  public Instant lastPollAt() {
    return lastPollAt.get();
  }

  public Instant lastCronAt() {
    return lastCronAt.get();
  }

  public int lastPollDispatched() {
    return lastPollDispatched.get();
  }

  public int lastCleanupDeleted() {
    return lastCleanupDeleted.get();
  }
}
