# Memory leak

## What you are learning

A leak is retained heap that never becomes unreachable — caches without bounds, static collections, listener lists, or forgotten closeables.

## Trigger

```bash
curl -X POST "http://localhost:8080/api/observability/playground/memory-leak?mb=64&holdSeconds=0" \
  -H "Authorization: Bearer $TOKEN"

# release when done
curl -X DELETE "http://localhost:8080/api/observability/playground/memory-leak" \
  -H "Authorization: Bearer $TOKEN"
```

## Watch in Grafana

Row **Memory leak**:

- `jvm_memory_used_bytes{area="heap"}` climbs
- `voyage_chaos_leak_bytes` tracks lab retention
- **Heap SLI** burns when usage stays above 85% of max

## PromQL

```promql
sum(jvm_memory_used_bytes{application="voyage-app",area="heap"})
  /
sum(jvm_memory_max_bytes{application="voyage-app",area="heap"})
voyage:heap_sli:ratio_rate5m
```

## Diagnose

1. Confirm growth is monotonic across GC cycles (not just a temporary allocation spike).
2. Take a heap dump (`jcmd GC.heap_dump`) and look for dominator trees.
3. Check caches/TTL — see Redis hotel cache TTLs in `AppConfig` / `application.yml`.

## Mitigate in this codebase

- Call `DELETE /memory-leak` to clear lab chunks.
- Production: bounded caches, weak refs where appropriate, TTL on Redis keys, avoid static mutable collections.
