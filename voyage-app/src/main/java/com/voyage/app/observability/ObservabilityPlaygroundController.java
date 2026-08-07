package com.voyage.app.observability;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability/playground")
@ConditionalOnProperty(
    name = "application.observability.chaos.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObservabilityPlaygroundController {

  private final ObservabilityChaosService observabilityChaosService;

  public ObservabilityPlaygroundController(ObservabilityChaosService observabilityChaosService) {
    this.observabilityChaosService = observabilityChaosService;
  }

  @PostMapping("/cpu-spike")
  public CpuSpikeResult cpuSpike(
      @RequestParam(defaultValue = "5") int seconds,
      @RequestParam(defaultValue = "2") int threads) {
    return observabilityChaosService.cpuSpike(seconds, threads);
  }

  @PostMapping("/memory-leak")
  public MemoryLeakResult memoryLeak(
      @RequestParam(defaultValue = "32") int mb,
      @RequestParam(defaultValue = "0") int holdSeconds) {
    return observabilityChaosService.memoryLeak(mb, holdSeconds);
  }

  @DeleteMapping("/memory-leak")
  public MemoryReleaseResult releaseMemoryLeak() {
    return observabilityChaosService.releaseMemoryLeak();
  }

  @PostMapping("/slow-api")
  public SlowApiResult slowApi(@RequestParam(defaultValue = "750") long delayMs) {
    return observabilityChaosService.slowApi(delayMs);
  }

  @PostMapping("/db-deadlock")
  public DbDeadlockResult dbDeadlock() {
    return observabilityChaosService.dbDeadlock();
  }

  @PostMapping("/kafka-lag")
  public KafkaLagResult kafkaLag(
      @RequestParam(defaultValue = "200") int messageCount,
      @RequestParam(defaultValue = "15") int pauseSeconds) {
    return observabilityChaosService.kafkaLag(messageCount, pauseSeconds);
  }

  @PostMapping("/redis-eviction")
  public RedisEvictionResult redisEviction(
      @RequestParam(defaultValue = "2000") int keyCount,
      @RequestParam(defaultValue = "32768") int valueBytes) {
    return observabilityChaosService.redisEviction(keyCount, valueBytes);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleConflict(IllegalStateException exception) {
    return Map.of("error", exception.getMessage());
  }
}
