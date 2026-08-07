# RabbitMQ Skills Lab

Hands-on AMQP for **Exchange**, **Queue**, **Routing Key**, and **Consumer** — using a voyage-domain **task queue** (`voyage.jobs`) for background jobs like booking confirmation and email send.

Contrast with Kafka hotel event streaming at `/ui/kafka` and [`03_kafka_vs_rabbitmq.md`](03_kafka_vs_rabbitmq.md).

## Prerequisites

1. Start RabbitMQ (and the rest of infra): `docker compose up -d`
2. Start the app: `./mvnw spring-boot:run -pl voyage-app`
3. Optional: open the broker UI at http://localhost:15672 (`guest` / `guest`)

## Concepts in this lab

```text
Publisher
    |
    v
voyage.jobs (direct exchange)
    |  routing key
    +-- booking.confirm --> voyage.jobs.booking.confirm
    +-- email.send      --> voyage.jobs.email.send
    |
    v
LabJobConsumer
```

| Concept | In this lab |
|---|---|
| Exchange | `voyage.jobs` (direct) — receives publishes |
| Queue | `voyage.jobs.booking.confirm`, `voyage.jobs.email.send` |
| Routing key | `booking.confirm`, `email.send` (must match a binding) |
| Consumer | Spring `@RabbitListener` workers that record deliveries |

## Script order

| File | Topic |
|---|---|
| `00_topology.sh` | Declare exchange, queues, bindings via management API |
| `01_publish_routing.sh` | Publish matching vs unbound routing keys |
| `02_consume_ack.sh` | Consume / ack notes (pair with the app consumer) |
| `03_kafka_vs_rabbitmq.md` | When to use Kafka vs RabbitMQ |

Scripts use `curl` against the RabbitMQ HTTP API (`localhost:15672`).

## App playground (same labs via API/UI)

- UI: http://localhost:8080/ui/rabbitmq
- API: `/api/rabbitmq/playground/*` (ADMIN JWT)
- Management UI: http://localhost:15672

See the root README **RabbitMQ lab** section for curl examples.

For the existing hotel **event stream**, see `/ui/kafka` and the Kafka section of the root README.
