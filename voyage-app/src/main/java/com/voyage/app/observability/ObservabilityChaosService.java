package com.voyage.app.observability;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.jpa.JpaPlaygroundService;
import com.voyage.app.kafka.HotelEventPublisher;
import com.voyage.app.kafka.HotelEventType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "application.observability.chaos.enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityChaosService {

    private static final int MAX_CPU_SECONDS = 30;
    private static final int MAX_CPU_THREADS = 8;
    private static final int MAX_LEAK_MB = 256;
    private static final int MAX_SLOW_MS = 10_000;
    private static final int MAX_KAFKA_BURST = 5_000;
    private static final int MAX_KAFKA_PAUSE_SECONDS = 60;
    private static final int MAX_REDIS_KEYS = 50_000;
    private static final int MAX_REDIS_VALUE_BYTES = 65_536;
    private static final String REDIS_EVICTION_KEY_PREFIX = "voyage:chaos:evict:";

    private final ObservabilityChaosMetrics metrics;
    private final JpaPlaygroundService jpaPlaygroundService;
    private final HotelRepository hotelRepository;
    private final ObjectProvider<HotelEventPublisher> hotelEventPublisher;
    private final ObjectProvider<KafkaListenerEndpointRegistry> kafkaListenerEndpointRegistry;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final boolean kafkaEnabled;
    private final boolean redisEnabled;

    private final List<byte[]> leakedChunks = new ArrayList<>();
    private final Object leakLock = new Object();
    private final AtomicBoolean cpuRunning = new AtomicBoolean(false);

    public ObservabilityChaosService(ObservabilityChaosMetrics metrics,
                                     JpaPlaygroundService jpaPlaygroundService,
                                     HotelRepository hotelRepository,
                                     ObjectProvider<HotelEventPublisher> hotelEventPublisher,
                                     ObjectProvider<KafkaListenerEndpointRegistry> kafkaListenerEndpointRegistry,
                                     ObjectProvider<StringRedisTemplate> stringRedisTemplate,
                                     @Value("${application.kafka.enabled:true}") boolean kafkaEnabled,
                                     @Value("${application.redis.enabled:true}") boolean redisEnabled) {
        this.metrics = metrics;
        this.jpaPlaygroundService = jpaPlaygroundService;
        this.hotelRepository = hotelRepository;
        this.hotelEventPublisher = hotelEventPublisher;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaEnabled = kafkaEnabled;
        this.redisEnabled = redisEnabled;
    }

    public CpuSpikeResult cpuSpike(int seconds, int threads) {
        int safeSeconds = clamp(seconds, 1, MAX_CPU_SECONDS);
        int safeThreads = clamp(threads, 1, MAX_CPU_THREADS);
        if (!cpuRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("CPU spike already running");
        }

        ExecutorService executor = Executors.newFixedThreadPool(safeThreads);
        metrics.setCpuActiveThreads(safeThreads);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(safeSeconds);

        for (int i = 0; i < safeThreads; i++) {
            executor.submit(() -> {
                long sink = 0L;
                while (System.nanoTime() < deadline) {
                    sink += System.nanoTime();
                }
                // Prevent the loop from being optimized away entirely
                if (sink == Long.MIN_VALUE) {
                    throw new IllegalStateException("unreachable");
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(safeSeconds + 5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new IllegalStateException("CPU spike interrupted", interrupted);
        } finally {
            metrics.setCpuActiveThreads(0);
            cpuRunning.set(false);
        }

        return new CpuSpikeResult(
                safeThreads,
                safeSeconds,
                "Watch process_cpu_usage and voyage_chaos_cpu_active on the Voyage SRE Lab dashboard."
        );
    }

    public MemoryLeakResult memoryLeak(int mb, int holdSeconds) {
        int safeMb = clamp(mb, 1, MAX_LEAK_MB);
        int safeHold = clamp(holdSeconds, 0, 300);
        byte[] chunk = new byte[safeMb * 1024 * 1024];
        // Touch pages so the JVM actually commits memory
        for (int i = 0; i < chunk.length; i += 4096) {
            chunk[i] = 1;
        }

        synchronized (leakLock) {
            leakedChunks.add(chunk);
            metrics.setLeakBytesHeld(leakedChunks.stream().mapToLong(c -> c.length).sum());
        }

        if (safeHold > 0) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(safeHold));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Memory leak hold interrupted", interrupted);
            }
        }

        long held;
        synchronized (leakLock) {
            held = leakedChunks.stream().mapToLong(c -> c.length).sum();
        }

        return new MemoryLeakResult(
                safeMb,
                held,
                safeHold,
                "Heap should climb on jvm_memory_used_bytes. Call DELETE /memory-leak to release."
        );
    }

    public MemoryReleaseResult releaseMemoryLeak() {
        long released;
        synchronized (leakLock) {
            released = leakedChunks.stream().mapToLong(c -> c.length).sum();
            leakedChunks.clear();
            metrics.setLeakBytesHeld(0);
        }
        System.gc();
        return new MemoryReleaseResult(released, "Retained lab chunks cleared; heap should fall after GC.");
    }

    public SlowApiResult slowApi(long delayMs) {
        long safeDelay = Math.min(Math.max(delayMs, 1L), MAX_SLOW_MS);
        metrics.incrementSlowApi();
        try {
            Thread.sleep(safeDelay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Slow API interrupted", interrupted);
        }
        return new SlowApiResult(
                safeDelay,
                "http_server_requests_seconds for uri=/api/observability/playground/slow-api should breach the 500ms SLO."
        );
    }

    public DbDeadlockResult dbDeadlock() {
        var result = jpaPlaygroundService.lockContention();
        metrics.incrementDeadlocks();
        return new DbDeadlockResult(
                result.inventoryId(),
                result.stayDate(),
                result.holder(),
                result.contender(),
                result.elapsedMs(),
                result.roomsAfter(),
                result.tip() + " Metric: voyage_chaos_deadlocks_total."
        );
    }

    public KafkaLagResult kafkaLag(int messageCount, int pauseSeconds) {
        if (!kafkaEnabled) {
            throw new IllegalStateException("Kafka is disabled; enable application.kafka.enabled to run the lag lab");
        }
        HotelEventPublisher publisher = hotelEventPublisher.getIfAvailable();
        KafkaListenerEndpointRegistry registry = kafkaListenerEndpointRegistry.getIfAvailable();
        if (publisher == null || registry == null) {
            throw new IllegalStateException("Kafka beans are unavailable; enable application.kafka.enabled to run the lag lab");
        }

        int safeCount = clamp(messageCount, 1, MAX_KAFKA_BURST);
        int safePause = clamp(pauseSeconds, 1, MAX_KAFKA_PAUSE_SECONDS);

        Hotel hotel = hotelRepository.findAll().stream().findFirst()
                .orElseGet(() -> hotelRepository.save(new Hotel("Chaos Lag Hotel", "Lag City", 99.0)));

        pauseListeners(registry, true);
        metrics.setKafkaConsumerPaused(true);
        try {
            for (int i = 0; i < safeCount; i++) {
                publisher.publish(HotelEventType.UPDATED, hotel);
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(safePause));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka lag lab interrupted", interrupted);
        } finally {
            pauseListeners(registry, false);
            metrics.setKafkaConsumerPaused(false);
            metrics.incrementKafkaLagRuns();
        }

        return new KafkaLagResult(
                safeCount,
                safePause,
                false,
                "Consumer was paused while publishing; kafka_consumergroup_lag should spike then drain."
        );
    }

    public RedisEvictionResult redisEviction(int keyCount, int valueBytes) {
        if (!redisEnabled) {
            throw new IllegalStateException("Redis is disabled; enable application.redis.enabled to run the eviction lab");
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException("Redis template is unavailable; enable application.redis.enabled to run the eviction lab");
        }

        int safeKeys = clamp(keyCount, 1, MAX_REDIS_KEYS);
        int safeBytes = clamp(valueBytes, 1024, MAX_REDIS_VALUE_BYTES);
        String payload = "x".repeat(safeBytes);
        String runId = UUID.randomUUID().toString();

        for (int i = 0; i < safeKeys; i++) {
            redis.opsForValue().set(REDIS_EVICTION_KEY_PREFIX + runId + ":" + i, payload);
        }
        metrics.incrementRedisEvictionRuns();

        return new RedisEvictionResult(
                safeKeys,
                safeBytes,
                "With Redis maxmemory=64mb + allkeys-lru, redis_evicted_keys_total should rise as keys fill the limit."
        );
    }

    private void pauseListeners(KafkaListenerEndpointRegistry registry, boolean pause) {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            if (pause) {
                container.pause();
            } else {
                container.resume();
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}

record CpuSpikeResult(int threads, int seconds, String tip) {
}

record MemoryLeakResult(long mbAllocated, long bytesHeld, int holdSeconds, String tip) {
}

record MemoryReleaseResult(long bytesReleased, String tip) {
}

record SlowApiResult(long delayMs, String tip) {
}

record DbDeadlockResult(
        Long inventoryId,
        String stayDate,
        String holder,
        String contender,
        long elapsedMs,
        int roomsAfter,
        String tip
) {
}

record KafkaLagResult(int published, int pauseSeconds, boolean consumerPaused, String tip) {
}

record RedisEvictionResult(int keysWritten, int valueBytes, String tip) {
}
