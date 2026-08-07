#!/usr/bin/env bash
# Manual get/ack against a lab queue. Prefer the app consumer when learning Spring AMQP.
set -euo pipefail

RABBIT_API="${RABBIT_API:-http://localhost:15672/api}"
RABBIT_USER="${RABBIT_USER:-guest}"
RABBIT_PASS="${RABBIT_PASS:-guest}"
VHOST="${VHOST:-%2F}"
QUEUE="${QUEUE:-voyage.jobs.booking.confirm}"

auth=(-u "$RABBIT_USER:$RABBIT_PASS")

echo "Getting one message from $QUEUE (ackmode=ack_requeue_false => ack and remove)"
curl -sS "${auth[@]}" -X POST "$RABBIT_API/queues/$VHOST/$QUEUE/get" \
  -H 'Content-Type: application/json' \
  -d '{"count":1,"ackmode":"ack_requeue_false","encoding":"auto"}' | jq .

echo
cat <<'EOF'
Ack semantics (interview notes):

- Consumer receives a delivery from a Queue (not from the Exchange).
- ack  = work done; broker drops the message.
- nack/reject + requeue = try again (or send to a DLX in production).
- Prefetch (QoS) limits unacked messages in flight per consumer.

In Voyage:
- LabJobConsumer uses @RabbitListener with default auto-ack success path.
- Watch deliveries in the UI: GET /api/rabbitmq/playground/consumed
- For at-least-once event streaming / replay, study Kafka at /ui/kafka instead.
EOF
