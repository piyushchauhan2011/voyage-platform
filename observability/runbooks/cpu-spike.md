# CPU spike

## What you are learning

A sudden CPU climb usually means busy loops, expensive serialization, hot locks, or too many concurrent workers — not “the box is slow.”

## Trigger

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"..."}' | jq -r .accessToken)

curl -X POST "http://localhost:8080/api/observability/playground/cpu-spike?seconds=10&threads=4" \
  -H "Authorization: Bearer $TOKEN"
```

## Watch in Grafana

Open **Voyage SRE Lab** → row **CPU spike**.

- `process_cpu_usage` should approach red (≥80%)
- `voyage_chaos_cpu_active` shows busy threads
- **CPU SLI** gauge should dip below 95%

## PromQL

```promql
process_cpu_usage{application="voyage-app"}
voyage:cpu_sli:ratio_rate5m
```

## Diagnose like a senior engineer

1. Confirm it is *your* process (`process_cpu_usage` vs `system_cpu_usage`).
2. Capture a thread dump (`jstack` / `jcmd Thread.print`) while the spike is active.
3. Look for RUNNABLE threads in application packages (busy loops, regex, JSON, crypto).
4. Correlate with recent deploys / traffic spikes.

## Mitigate in this codebase

- Cap chaos threads (`MAX_CPU_THREADS` in `ObservabilityChaosService`).
- In real services: bound executor pools, avoid sync work on request threads, profile hot paths.
