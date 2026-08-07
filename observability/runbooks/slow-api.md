# Slow API

## What you are learning

Latency SLOs are usually defined on *good events* (requests under a threshold). One slow endpoint can burn the budget for the whole service.

## Trigger

```bash
curl -X POST "http://localhost:8080/api/observability/playground/slow-api?delayMs=750" \
  -H "Authorization: Bearer $TOKEN"
```

Repeat a few times so the 5m histogram has signal.

## Watch in Grafana

Row **Slow API**:

- p99 for `/api/observability/playground/slow-api` jumps above 500ms
- **Latency SLI** gauge drops below 99%
- Alert `VoyageHttpLatencySloBurn` may fire after ~2m

## PromQL

```promql
histogram_quantile(0.99,
  sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="voyage-app"}[5m])))
voyage:http_latency_sli:ratio_rate5m
```

## Diagnose

1. Identify the slow URI from the latency panel.
2. Break down dependency time (DB, Redis, Kafka, external HTTP).
3. Check N+1 / lock waits via the JPA and Postgres playgrounds.
4. Look at thread pool saturation and GC pauses if CPU/heap panels are also red.

## Mitigate in this codebase

- Keep request work short; move heavy work to Kafka/RabbitMQ jobs.
- Use inventory `@Retryable` carefully — retries amplify latency under contention.
- Histogram SLOs are configured under `management.metrics.distribution` in `application.yml`.
