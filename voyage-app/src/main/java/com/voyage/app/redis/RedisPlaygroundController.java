package com.voyage.app.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/redis/playground")
@ConditionalOnProperty(name = "application.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisPlaygroundController {

    private final RedisPlaygroundService redisPlaygroundService;

    public RedisPlaygroundController(RedisPlaygroundService redisPlaygroundService) {
        this.redisPlaygroundService = redisPlaygroundService;
    }

    @PostMapping("/strings/{key}")
    public RedisStringValue putString(@PathVariable String key, @RequestBody RedisStringWriteRequest request) {
        return redisPlaygroundService.putString(key, request.value(), request.ttlSeconds());
    }

    @GetMapping("/strings/{key}")
    public RedisStringValue getString(@PathVariable String key) {
        return redisPlaygroundService.getString(key);
    }

    @PostMapping("/hashes/{key}")
    public RedisHashValue putHash(@PathVariable String key, @RequestBody RedisHashWriteRequest request) {
        return redisPlaygroundService.putHash(key, request.fields(), request.ttlSeconds());
    }

    @GetMapping("/hashes/{key}")
    public RedisHashValue getHash(@PathVariable String key) {
        return redisPlaygroundService.getHash(key);
    }

    @PostMapping("/lists/{key}")
    public RedisListValue putList(@PathVariable String key, @RequestBody RedisCollectionWriteRequest request) {
        return redisPlaygroundService.putList(key, request.values(), request.ttlSeconds());
    }

    @GetMapping("/lists/{key}")
    public RedisListValue getList(@PathVariable String key) {
        return redisPlaygroundService.getList(key);
    }

    @PostMapping("/sets/{key}")
    public RedisSetValue putSet(@PathVariable String key, @RequestBody RedisCollectionWriteRequest request) {
        return redisPlaygroundService.putSet(key, request.values(), request.ttlSeconds());
    }

    @GetMapping("/sets/{key}")
    public RedisSetValue getSet(@PathVariable String key) {
        return redisPlaygroundService.getSet(key);
    }

    @PostMapping("/sorted-sets/{key}")
    public RedisSortedSetValue putSortedSet(@PathVariable String key, @RequestBody RedisSortedSetWriteRequest request) {
        return redisPlaygroundService.putSortedSet(key, request.values(), request.ttlSeconds());
    }

    @GetMapping("/sorted-sets/{key}")
    public RedisSortedSetValue getSortedSet(@PathVariable String key) {
        return redisPlaygroundService.getSortedSet(key);
    }

    @GetMapping("/ttl/{key}")
    public RedisTtlValue getTtl(@PathVariable String key) {
        return redisPlaygroundService.getTtl(key);
    }

    @PostMapping("/pubsub/publish")
    public RedisPubSubPublishResult publish(@RequestBody RedisPublishRequest request) {
        return redisPlaygroundService.publish(request.message());
    }

    @GetMapping("/pubsub/messages")
    public List<RedisPubSubMessageView> recentMessages() {
        return redisPlaygroundService.recentMessages();
    }

    @PostMapping("/locks/{lockName}/acquire")
    public RedisLockValue acquireLock(@PathVariable String lockName, @RequestBody RedisLockAcquireRequest request) {
        return redisPlaygroundService.acquireLock(lockName, request.owner(), request.ttlSeconds());
    }

    @DeleteMapping("/locks/{lockName}")
    public RedisLockReleaseResult releaseLock(@PathVariable String lockName, @RequestParam String owner) {
        return redisPlaygroundService.releaseLock(lockName, owner);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}

record RedisStringWriteRequest(String value, Long ttlSeconds) {
}

record RedisHashWriteRequest(Map<String, String> fields, Long ttlSeconds) {
}

record RedisCollectionWriteRequest(List<String> values, Long ttlSeconds) {
}

record RedisSortedSetWriteRequest(List<RedisSortedSetEntry> values, Long ttlSeconds) {
}

record RedisPublishRequest(String message) {
}

record RedisLockAcquireRequest(String owner, Long ttlSeconds) {
}