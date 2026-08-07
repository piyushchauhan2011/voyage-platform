# Observability lab

Local Prometheus + Grafana stack for learning the six SRE failure modes against `voyage-app`.

## Start

```bash
docker compose up -d
./mvnw spring-boot:run -pl voyage-app
```

| Service | URL | Notes |
|---|---|---|
| Prometheus | http://localhost:9090 | Scrapes Actuator + exporters |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| App metrics | http://localhost:8080/actuator/prometheus | Public scrape path (lab only) |

Dashboard: **Voyage → Voyage SRE Lab**

## Chaos endpoints (ADMIN JWT)

Register an admin (or promote a user), then:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"YOUR_ADMIN","password":"YOUR_PASSWORD"}' | jq -r .accessToken)
```

| Failure | Endpoint |
|---|---|
| CPU spike | `POST /api/observability/playground/cpu-spike?seconds=5&threads=2` |
| Memory leak | `POST /api/observability/playground/memory-leak?mb=32` |
| Release leak | `DELETE /api/observability/playground/memory-leak` |
| Slow API | `POST /api/observability/playground/slow-api?delayMs=750` |
| DB lock contention | `POST /api/observability/playground/db-deadlock` |
| Kafka lag | `POST /api/observability/playground/kafka-lag?messageCount=200&pauseSeconds=15` |
| Redis eviction | `POST /api/observability/playground/redis-eviction?keyCount=2000&valueBytes=32768` |

Disable injectors with `application.observability.chaos.enabled=false` (already set in k8s ConfigMap).

## Runbooks

- [CPU spike](runbooks/cpu-spike.md)
- [Memory leak](runbooks/memory-leak.md)
- [Slow API](runbooks/slow-api.md)
- [DB deadlock](runbooks/db-deadlock.md)
- [Kafka lag](runbooks/kafka-lag.md)
- [Redis eviction](runbooks/redis-eviction.md)
