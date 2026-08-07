# Database deadlock / lock contention

## What you are learning

Postgres row locks (`FOR UPDATE`) serialize writers. Contention looks like latency, retries, or true deadlocks when lock order differs across transactions.

## Trigger

```bash
curl -X POST "http://localhost:8080/api/observability/playground/db-deadlock" \
  -H "Authorization: Bearer $TOKEN"
```

This reuses `JpaPlaygroundService.lockContention()` (same as `/api/jpa/playground/tx/deadlock-retry`).

## Watch in Grafana

Row **Database deadlock**:

- `voyage_chaos_deadlocks_total` rate ticks up
- Postgres lock panels may move
- **Deadlock quiet SLI** dips while the lab runs

## PromQL

```promql
rate(voyage_chaos_deadlocks_total[5m])
pg_stat_database_deadlocks
```

## Diagnose

1. Check `pg_locks` / `pg_stat_activity` for waiting sessions.
2. Confirm lock order is consistent across code paths.
3. Look for long transactions holding locks (`LOCK_HOLD_MS` style sleeps are a smell in production).
4. Review retry policy on `CannotAcquireLockException` in `InventoryService`.

## Mitigate in this codebase

- Keep transactions short.
- Reserve inventory with consistent lock ordering.
- Prefer `@Retryable` for transient lock failures, not as a substitute for shorter critical sections.
