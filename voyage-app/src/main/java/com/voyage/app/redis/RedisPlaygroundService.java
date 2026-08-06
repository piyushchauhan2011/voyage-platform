package com.voyage.app.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@ConditionalOnProperty(name = "application.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisPlaygroundService {

    private static final String LOCK_KEY_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisPubSubRecorder redisPubSubRecorder;
    private final String redisPubSubChannel;

    public RedisPlaygroundService(StringRedisTemplate stringRedisTemplate,
                                  RedisPubSubRecorder redisPubSubRecorder,
                                  @Value("${application.redis.pubsub.channel:voyage:notifications}") String redisPubSubChannel) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisPubSubRecorder = redisPubSubRecorder;
        this.redisPubSubChannel = redisPubSubChannel;
    }

    public RedisStringValue putString(String key, String value, Long ttlSeconds) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }

        if (ttlSeconds != null) {
            stringRedisTemplate.opsForValue().set(key, value, ttlDuration(ttlSeconds));
        } else {
            stringRedisTemplate.opsForValue().set(key, value);
        }

        return getString(key);
    }

    public RedisStringValue getString(String key) {
        validateKey(key);
        return new RedisStringValue(key, stringRedisTemplate.opsForValue().get(key), ttlSeconds(key));
    }

    public RedisHashValue putHash(String key, Map<String, String> fields, Long ttlSeconds) {
        validateKey(key);
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields are required");
        }

        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForHash().putAll(key, fields);
        applyOptionalTtl(key, ttlSeconds);
        return getHash(key);
    }

    public RedisHashValue getHash(String key) {
        validateKey(key);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, String> fields = new LinkedHashMap<>();
        entries.forEach((field, value) -> fields.put(String.valueOf(field), value != null ? String.valueOf(value) : null));
        return new RedisHashValue(key, fields, ttlSeconds(key));
    }

    public RedisListValue putList(String key, List<String> values, Long ttlSeconds) {
        validateKey(key);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values are required");
        }

        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForList().rightPushAll(key, values);
        applyOptionalTtl(key, ttlSeconds);
        return getList(key);
    }

    public RedisListValue getList(String key) {
        validateKey(key);
        List<String> values = stringRedisTemplate.opsForList().range(key, 0, -1);
        return new RedisListValue(key, values != null ? values : List.of(), ttlSeconds(key));
    }

    public RedisSetValue putSet(String key, List<String> values, Long ttlSeconds) {
        validateKey(key);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values are required");
        }

        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForSet().add(key, values.toArray(String[]::new));
        applyOptionalTtl(key, ttlSeconds);
        return getSet(key);
    }

    public RedisSetValue getSet(String key) {
        validateKey(key);
        Set<String> values = stringRedisTemplate.opsForSet().members(key);
        return new RedisSetValue(key, values == null ? List.of() : new ArrayList<>(new TreeSet<>(values)), ttlSeconds(key));
    }

    public RedisSortedSetValue putSortedSet(String key, List<RedisSortedSetEntry> values, Long ttlSeconds) {
        validateKey(key);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values are required");
        }

        stringRedisTemplate.delete(key);
        for (RedisSortedSetEntry entry : values) {
            stringRedisTemplate.opsForZSet().add(key, entry.value(), entry.score());
        }
        applyOptionalTtl(key, ttlSeconds);
        return getSortedSet(key);
    }

    public RedisSortedSetValue getSortedSet(String key) {
        validateKey(key);
        Set<ZSetOperations.TypedTuple<String>> entries = stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        List<RedisSortedSetEntry> values = entries == null ? List.of() : entries.stream()
                .map(entry -> new RedisSortedSetEntry(entry.getValue(), entry.getScore() != null ? entry.getScore() : 0.0))
                .toList();
        return new RedisSortedSetValue(key, values, ttlSeconds(key));
    }

    public RedisTtlValue getTtl(String key) {
        validateKey(key);
        return new RedisTtlValue(key, ttlSeconds(key));
    }

    public RedisPubSubPublishResult publish(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }

        Long subscribers = stringRedisTemplate.convertAndSend(redisPubSubChannel, message);
        return new RedisPubSubPublishResult(redisPubSubChannel, message, subscribers != null ? subscribers.intValue() : 0);
    }

    public List<RedisPubSubMessageView> recentMessages() {
        return redisPubSubRecorder.recentMessages();
    }

    public RedisLockValue acquireLock(String lockName, String owner, Long ttlSeconds) {
        validateLockRequest(lockName, owner, ttlSeconds);
        String lockKey = lockKey(lockName);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, owner, ttlDuration(ttlSeconds));
        return new RedisLockValue(lockName, owner, Boolean.TRUE.equals(acquired), ttlSeconds(lockKey));
    }

    public RedisLockReleaseResult releaseLock(String lockName, String owner) {
        validateKey(lockName);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }

        Long deleted = stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey(lockName)), owner);
        return new RedisLockReleaseResult(lockName, owner, deleted != null && deleted > 0);
    }

    private void applyOptionalTtl(String key, Long ttlSeconds) {
        if (ttlSeconds != null) {
            stringRedisTemplate.expire(key, ttlDuration(ttlSeconds));
        }
    }

    private void validateLockRequest(String lockName, String owner, Long ttlSeconds) {
        validateKey(lockName);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (ttlSeconds == null) {
            throw new IllegalArgumentException("ttlSeconds is required for locks");
        }
        ttlDuration(ttlSeconds);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
    }

    private Duration ttlDuration(Long ttlSeconds) {
        if (ttlSeconds == null || ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be greater than 0");
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    private Long ttlSeconds(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    private String lockKey(String lockName) {
        return LOCK_KEY_PREFIX + lockName;
    }
}

record RedisStringValue(String key, String value, Long ttlSecondsRemaining) {
}

record RedisHashValue(String key, Map<String, String> fields, Long ttlSecondsRemaining) {
}

record RedisListValue(String key, List<String> values, Long ttlSecondsRemaining) {
}

record RedisSetValue(String key, List<String> values, Long ttlSecondsRemaining) {
}

record RedisSortedSetValue(String key, List<RedisSortedSetEntry> values, Long ttlSecondsRemaining) {
}

record RedisSortedSetEntry(String value, Double score) {
}

record RedisTtlValue(String key, Long ttlSecondsRemaining) {
}

record RedisPubSubPublishResult(String channel, String message, int subscriberCount) {
}

record RedisLockValue(String lockName, String owner, boolean acquired, Long ttlSecondsRemaining) {
}

record RedisLockReleaseResult(String lockName, String owner, boolean released) {
}