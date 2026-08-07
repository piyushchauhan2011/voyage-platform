# Kafka lag

## What you are learning

Consumer lag means the broker has messages the consumer group has not committed yet — slow processing, paused consumers, partition imbalance, or poison messages.

## Trigger

```bash
curl -X POST "http://localhost:8080/api/observability/playground/kafka-lag?messageCount=500&pauseSeconds=20" \
  -H "Authorization: Bearer $TOKEN"
```

The lab pauses hotel-event listeners, publishes a burst, waits, then resumes.

## Watch in Grafana

Row **Kafka lag**:

- `kafka_consumergroup_lag` spikes then drains
- `voyage_chaos_kafka_consumer_paused` flips to 1 during the pause
- **Kafka lag SLI** burns while lag > 100

## PromQL

```promql
sum by (consumergroup, topic) (kafka_consumergroup_lag)
voyage:kafka_lag_sli:ratio_rate5m
```

## Diagnose

1. Confirm which group/topic is lagging (`voyage-hotel-events-ui` / `hotel-events`).
2. Check consumer is running (not paused, not crash-looping).
3. Inspect DLT / poison payloads via the Kafka UI (`/ui/kafka`) and DLT listeners.
4. Measure per-message processing time vs produce rate.

## Mitigate in this codebase

- Scale consumers or increase concurrency carefully.
- Fix poison messages and use the DLT replay path.
- Keep handlers idempotent (`HotelEventStatusService` recording).
