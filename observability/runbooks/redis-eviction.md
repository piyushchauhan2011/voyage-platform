# Redis eviction

## What you are learning

When Redis hits `maxmemory`, the eviction policy deletes keys. With `allkeys-lru`, even “important” keys without TTL can disappear — which looks like random cache misses.

## Trigger

Compose Redis is capped at **64mb** + `allkeys-lru`. Fill it:

```bash
curl -X POST "http://localhost:8080/api/observability/playground/redis-eviction?keyCount=3000&valueBytes=32768" \
  -H "Authorization: Bearer $TOKEN"
```

## Watch in Grafana

Row **Redis eviction**:

- `rate(redis_evicted_keys_total[1m])` rises
- memory used / max approaches 1
- hit ratio may fall as keys vanish
- **Redis eviction SLI** burns

## PromQL

```promql
rate(redis_evicted_keys_total[1m])
redis_memory_used_bytes / clamp_min(redis_memory_max_bytes, 1)
voyage:redis_eviction_sli:ratio_rate5m
```

## Diagnose

1. Confirm `maxmemory` and `maxmemory-policy` (`INFO memory`).
2. Identify large key prefixes (`voyage:chaos:evict:` for this lab; hotel cache keys in app traffic).
3. Distinguish intentional TTL expiry from eviction under pressure.
4. Check whether misses stampede the database (hotel-by-id cache).

## Mitigate in this codebase

- Set TTLs on cache entries (`application.redis.cache.ttl.*`).
- Size `maxmemory` for working set + headroom.
- Prefer `volatile-lru` if only TTL keys should be evictable.
- Avoid unbounded fills without TTL in application code.
