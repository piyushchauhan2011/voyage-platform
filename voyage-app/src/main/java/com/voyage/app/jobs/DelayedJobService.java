package com.voyage.app.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis sorted-set delay queue: member = job JSON, score = execute-at epoch millis (Laravel {@code
 * delay()} analogue).
 */
@Service
@ConditionalOnProperty(
    name = "application.redis.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DelayedJobService {

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final String delayedKey;

  public DelayedJobService(
      StringRedisTemplate stringRedisTemplate,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${application.jobs.delayed-key:voyage:delayed-jobs}") String delayedKey) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.delayedKey = delayedKey;
  }

  public DelayedJob schedule(String routingKey, String payload, Duration delay, JobSource source) {
    if (routingKey == null || routingKey.isBlank()) {
      throw new IllegalArgumentException("routingKey is required");
    }
    if (delay == null || delay.isNegative()) {
      throw new IllegalArgumentException("delay must be zero or positive");
    }
    Instant now = Instant.now(clock);
    DelayedJob job =
        new DelayedJob(
            UUID.randomUUID().toString(),
            routingKey.trim(),
            payload == null || payload.isBlank() ? "lab-job" : payload.trim(),
            source == null ? JobSource.DELAYED : source,
            now,
            now.plus(delay));
    stringRedisTemplate.opsForZSet().add(delayedKey, toJson(job), job.executeAt().toEpochMilli());
    return job;
  }

  public List<DelayedJob> listPending() {
    Set<ZSetOperations.TypedTuple<String>> tuples =
        stringRedisTemplate.opsForZSet().rangeWithScores(delayedKey, 0, -1);
    return toJobs(tuples);
  }

  /** Atomically claim jobs whose execute-at score is &lt;= now. */
  public List<DelayedJob> claimDue(Instant now) {
    long maxScore = now.toEpochMilli();
    Set<ZSetOperations.TypedTuple<String>> tuples =
        stringRedisTemplate.opsForZSet().rangeByScoreWithScores(delayedKey, 0, maxScore);
    if (tuples == null || tuples.isEmpty()) {
      return List.of();
    }
    List<DelayedJob> claimed = new ArrayList<>();
    for (ZSetOperations.TypedTuple<String> tuple : tuples) {
      String member = tuple.getValue();
      if (member == null) {
        continue;
      }
      Long removed = stringRedisTemplate.opsForZSet().remove(delayedKey, member);
      if (removed != null && removed > 0) {
        claimed.add(fromJson(member));
      }
    }
    return claimed;
  }

  public long purge() {
    Long size = stringRedisTemplate.opsForZSet().zCard(delayedKey);
    stringRedisTemplate.delete(delayedKey);
    return size == null ? 0 : size;
  }

  public long pendingCount() {
    Long size = stringRedisTemplate.opsForZSet().zCard(delayedKey);
    return size == null ? 0 : size;
  }

  public String delayedKey() {
    return delayedKey;
  }

  private List<DelayedJob> toJobs(Set<ZSetOperations.TypedTuple<String>> tuples) {
    if (tuples == null || tuples.isEmpty()) {
      return List.of();
    }
    List<DelayedJob> jobs = new ArrayList<>(tuples.size());
    for (ZSetOperations.TypedTuple<String> tuple : tuples) {
      if (tuple.getValue() != null) {
        jobs.add(fromJson(tuple.getValue()));
      }
    }
    return jobs;
  }

  private String toJson(DelayedJob job) {
    try {
      return objectMapper.writeValueAsString(job);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialise delayed job", ex);
    }
  }

  private DelayedJob fromJson(String json) {
    try {
      return objectMapper.readValue(json, DelayedJob.class);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to deserialise delayed job: " + json, ex);
    }
  }
}
