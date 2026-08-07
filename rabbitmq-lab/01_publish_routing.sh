#!/usr/bin/env bash
# Publish with matching vs unbound routing keys; inspect queue depths.
set -euo pipefail

RABBIT_API="${RABBIT_API:-http://localhost:15672/api}"
RABBIT_USER="${RABBIT_USER:-guest}"
RABBIT_PASS="${RABBIT_PASS:-guest}"
VHOST="${VHOST:-%2F}"

auth=(-u "$RABBIT_USER:$RABBIT_PASS")

publish() {
  local routing_key="$1"
  local payload="$2"
  echo "Publishing routing_key=$routing_key payload=$payload"
  curl -sS "${auth[@]}" -X POST "$RABBIT_API/exchanges/$VHOST/voyage.jobs/publish" \
    -H 'Content-Type: application/json' \
    -d "{\"properties\":{},\"routing_key\":\"$routing_key\",\"payload\":\"$payload\",\"payload_encoding\":\"string\"}" | jq .
}

queue_depth() {
  local queue="$1"
  curl -sS "${auth[@]}" "$RABBIT_API/queues/$VHOST/$queue" | jq '{name, messages, consumers}'
}

echo "=== Matching keys (should land in queues if no consumer is draining them) ==="
publish "booking.confirm" "confirm-booking-42"
publish "email.send" "send-email-42"

echo
echo "Queue depths:"
queue_depth "voyage.jobs.booking.confirm"
queue_depth "voyage.jobs.email.send"

echo
echo "=== Unbound key (routed_to_queues should be empty / message dropped) ==="
publish "unknown.job" "should-not-land"

echo
echo "Observation: a direct exchange only delivers when routing_key equals a binding key."
echo "If the Voyage app is running, LabJobConsumer may already have drained matching messages — check /ui/rabbitmq → Refresh consumed."
