# Kafka vs RabbitMQ

Both move messages between services. They optimize for different jobs.

## Side-by-side

| Aspect | Kafka | RabbitMQ |
|---|---|---|
| Model | Event streaming / append-only log | Task queues / background jobs |
| Strength | Large scale, analytics, retention, replay | Work distribution, flexible routing, per-message ack |
| Voyage example | `hotel-events` domain stream (`/ui/kafka`) | `voyage.jobs` confirmation & email jobs (`/ui/rabbitmq`) |
| Routing idea | Topic + partition key | Exchange + routing key → queue |
| Consumer view | Consumer group reads offsets from the log | Competing consumers take work from a queue |

## When to use which

**Prefer Kafka** when:

- Many consumers need the **same** history (analytics, audit, fan-out readers)
- You care about **ordering within a key** and **replay** from an offset
- Throughput and retention dominate over per-message work queues

**Prefer RabbitMQ** when:

- Workers should process **discrete jobs** (send email, confirm booking)
- You want **exchange/routing** semantics (direct, topic, fanout, headers)
- Success means “a consumer **acked** this job,” not “it remains in a log”

## Map to Voyage

```text
Kafka path (already in the app)
HotelService → hotel-events topic → HotelEventListener → processed_hotel_events

RabbitMQ lab path
Playground publish → voyage.jobs exchange → queue by routing key → LabJobConsumer
```

Open both UIs and say out loud which problem each broker is solving.
