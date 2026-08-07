#!/usr/bin/env bash
# Declare the voyage.jobs direct exchange, two queues, and routing-key bindings.
set -euo pipefail

RABBIT_API="${RABBIT_API:-http://localhost:15672/api}"
RABBIT_USER="${RABBIT_USER:-guest}"
RABBIT_PASS="${RABBIT_PASS:-guest}"
VHOST="${VHOST:-%2F}"

auth=(-u "$RABBIT_USER:$RABBIT_PASS")

echo "Declaring direct exchange voyage.jobs"
curl -sS "${auth[@]}" -X PUT "$RABBIT_API/exchanges/$VHOST/voyage.jobs" \
  -H 'Content-Type: application/json' \
  -d '{"type":"direct","durable":true,"auto_delete":false}'

declare_queue_and_bind() {
  local queue="$1"
  local routing_key="$2"
  echo "Declaring queue $queue (bind routing key $routing_key)"
  curl -sS "${auth[@]}" -X PUT "$RABBIT_API/queues/$VHOST/$queue" \
    -H 'Content-Type: application/json' \
    -d '{"durable":true,"auto_delete":false}'
  curl -sS "${auth[@]}" -X POST "$RABBIT_API/bindings/$VHOST/e/voyage.jobs/q/$queue" \
    -H 'Content-Type: application/json' \
    -d "{\"routing_key\":\"$routing_key\",\"arguments\":{}}"
}

declare_queue_and_bind "voyage.jobs.booking.confirm" "booking.confirm"
declare_queue_and_bind "voyage.jobs.email.send" "email.send"

echo
echo "Bindings on voyage.jobs:"
curl -sS "${auth[@]}" "$RABBIT_API/exchanges/$VHOST/voyage.jobs/bindings/source" | jq .

echo
echo "Tip: open http://localhost:15672 → Exchanges → voyage.jobs to see the same topology."
