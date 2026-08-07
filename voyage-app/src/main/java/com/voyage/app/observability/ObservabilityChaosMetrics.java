package com.voyage.app.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Micrometer series for the SRE chaos lab. Prometheus recording rules and
 * Grafana panels key off these names.
 */
@Component
public class ObservabilityChaosMetrics {

    private final AtomicInteger cpuActiveThreads = new AtomicInteger(0);
    private final AtomicLong leakBytesHeld = new AtomicLong(0);
    private final AtomicBoolean kafkaConsumerPaused = new AtomicBoolean(false);
    private final Counter deadlocksTotal;
    private final Counter redisEvictionRunsTotal;
    private final Counter kafkaLagRunsTotal;
    private final Counter slowApiInvocationsTotal;

    public ObservabilityChaosMetrics(MeterRegistry meterRegistry) {
        this.deadlocksTotal = Counter.builder("voyage_chaos_deadlocks_total")
                .description("Lock-contention / deadlock lab runs that completed")
                .register(meterRegistry);
        this.redisEvictionRunsTotal = Counter.builder("voyage_chaos_redis_eviction_runs_total")
                .description("Redis eviction fill runs")
                .register(meterRegistry);
        this.kafkaLagRunsTotal = Counter.builder("voyage_chaos_kafka_lag_runs_total")
                .description("Kafka lag burst runs")
                .register(meterRegistry);
        this.slowApiInvocationsTotal = Counter.builder("voyage_chaos_slow_api_total")
                .description("Artificial slow-API invocations")
                .register(meterRegistry);

        Gauge.builder("voyage_chaos_cpu_active", cpuActiveThreads, AtomicInteger::get)
                .description("Busy-loop threads currently burning CPU")
                .register(meterRegistry);
        Gauge.builder("voyage_chaos_leak_bytes", leakBytesHeld, AtomicLong::get)
                .description("Bytes retained by the memory-leak lab")
                .register(meterRegistry);
        Gauge.builder("voyage_chaos_kafka_consumer_paused", kafkaConsumerPaused, value -> value.get() ? 1.0 : 0.0)
                .description("1 when hotel-events consumers are paused for the lag lab")
                .register(meterRegistry);
    }

    public void setCpuActiveThreads(int threads) {
        cpuActiveThreads.set(Math.max(0, threads));
    }

    public void setLeakBytesHeld(long bytes) {
        leakBytesHeld.set(Math.max(0L, bytes));
    }

    public void setKafkaConsumerPaused(boolean paused) {
        kafkaConsumerPaused.set(paused);
    }

    public void incrementDeadlocks() {
        deadlocksTotal.increment();
    }

    public void incrementRedisEvictionRuns() {
        redisEvictionRunsTotal.increment();
    }

    public void incrementKafkaLagRuns() {
        kafkaLagRunsTotal.increment();
    }

    public void incrementSlowApi() {
        slowApiInvocationsTotal.increment();
    }
}
