#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
KAFKA_TOPIC="${KAFKA_TOPIC:-hotel-events}"
RUN_ID="$(date +%s)"
USERNAME="dlt_admin_${RUN_ID}"
EMAIL="${USERNAME}@test.com"
PASSWORD="password123"
BROKEN_PAYLOAD="BROKEN_EVENT_${RUN_ID}"
BROKEN_MESSAGE_KEY="broken-${RUN_ID}"
REPLAY_EVENT_ID="replayed-event-${RUN_ID}"
REPLAY_HOTEL_ID="$((900000 + RUN_ID % 100000))"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

http_code() {
  sed -n 's/^HTTP\/[^ ]* \([0-9][0-9][0-9]\).*/\1/p' | tail -n 1
}

json_body() {
  awk '{ sub(/\r$/, "") } seen { print } $0 == "" { seen=1 }'
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  shift 3 || true
  local -a extra_args=()

  if (($# > 0)); then
    extra_args=("$@")
  fi

  if [[ -n "$body" ]]; then
    if ((${#extra_args[@]} > 0)); then
      curl -sS -i -X "$method" "$BASE_URL$path" \
        -H "Content-Type: application/json" \
        "${extra_args[@]}" \
        -d "$body"
    else
      curl -sS -i -X "$method" "$BASE_URL$path" \
        -H "Content-Type: application/json" \
        -d "$body"
    fi
  else
    if ((${#extra_args[@]} > 0)); then
      curl -sS -i -X "$method" "$BASE_URL$path" \
        "${extra_args[@]}"
    else
      curl -sS -i -X "$method" "$BASE_URL$path"
    fi
  fi
}

expect_status() {
  local response="$1"
  local expected="$2"
  local actual
  actual="$(printf '%s' "$response" | http_code)"
  if [[ "$actual" != "$expected" ]]; then
    printf '%s\n' "$response"
    fail "Expected HTTP $expected but got $actual"
  fi
}

sql_escape() {
  printf '%s' "$1" | sed "s/'/''/g"
}

wait_for_dead_letter() {
  local payload="$1"
  local attempts=25
  local response=""

  while ((attempts > 0)); do
    response="$(curl -sS "$BASE_URL/ui/kafka/dead-letters")"
    if printf '%s' "$response" | jq -e --arg payload "$payload" '
      map(select(.payload == $payload)) | length > 0
    ' >/dev/null; then
      printf '%s' "$response" | jq
      printf '%s' "$response" | jq -r --arg payload "$payload" '
        map(select(.payload == $payload)) | first | .id
      '
      return 0
    fi
    attempts=$((attempts - 1))
    if ((attempts == 0)); then
      printf '%s\n' "$response" | jq || true
      fail "Timed out waiting for payload '$payload' in the dead-letter feed"
    fi
    sleep 1
  done
}

wait_for_replayed_event() {
  local event_id="$1"
  local attempts=25
  local response=""

  while ((attempts > 0)); do
    response="$(curl -sS "$BASE_URL/ui/kafka/status")"
    if printf '%s' "$response" | jq -e --arg event_id "$event_id" '
      map(select(.eventId == $event_id)) | length > 0
    ' >/dev/null; then
      printf '%s' "$response" | jq
      return 0
    fi
    attempts=$((attempts - 1))
    if ((attempts == 0)); then
      printf '%s\n' "$response" | jq || true
      fail "Timed out waiting for replayed event '$event_id' in the processed-event feed"
    fi
    sleep 1
  done
}

wait_for_dead_letter_resolution() {
  local dead_letter_id="$1"
  local attempts=25
  local response=""

  while ((attempts > 0)); do
    response="$(curl -sS "$BASE_URL/ui/kafka/dead-letters")"
    if printf '%s' "$response" | jq -e --arg id "$dead_letter_id" '
      map(select((.id | tostring) == $id and .retryStatus == "RESOLVED")) | length > 0
    ' >/dev/null; then
      printf '%s' "$response" | jq
      return 0
    fi
    attempts=$((attempts - 1))
    if ((attempts == 0)); then
      printf '%s\n' "$response" | jq || true
      fail "Timed out waiting for dead-letter #$dead_letter_id to become RESOLVED"
    fi
    sleep 1
  done
}

require_command curl
require_command jq
require_command docker

log "Checking service health at $BASE_URL"
HEALTH_RESPONSE="$(request GET "/actuator/health" "")"
expect_status "$HEALTH_RESPONSE" "200"
printf '%s' "$HEALTH_RESPONSE" | json_body | jq

log "Registering a QA user dedicated to the DLT flow: $USERNAME"
REGISTER_RESPONSE="$(request POST "/api/auth/register" "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
expect_status "$REGISTER_RESPONSE" "201"
printf '%s' "$REGISTER_RESPONSE" | json_body | jq

log "Promoting $USERNAME to ADMIN in Postgres"
docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "update users set role = 'ADMIN' where username = '$(sql_escape "$USERNAME")';" >/dev/null

log "Logging in again to get an ADMIN bearer token"
LOGIN_RESPONSE="$(request POST "/api/auth/login" "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
expect_status "$LOGIN_RESPONSE" "200"
LOGIN_JSON="$(printf '%s' "$LOGIN_RESPONSE" | json_body)"
printf '%s' "$LOGIN_JSON" | jq
ADMIN_ACCESS_TOKEN="$(printf '%s' "$LOGIN_JSON" | jq -r '.accessToken')"

log "Publishing a malformed Kafka payload to trigger retries and the DLT"
PUBLISH_RESPONSE="$(request POST "/api/kafka/publish-raw" "{\"messageKey\":\"$BROKEN_MESSAGE_KEY\",\"payload\":\"$BROKEN_PAYLOAD\"}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$PUBLISH_RESPONSE" "202"
printf '%s' "$PUBLISH_RESPONSE" | json_body | jq

log "Waiting for the malformed payload to appear in /ui/kafka/dead-letters"
DEAD_LETTER_ID="$(wait_for_dead_letter "$BROKEN_PAYLOAD" | tail -n 1)"
log "Captured dead-letter id: $DEAD_LETTER_ID"

REPLAY_PAYLOAD="$(jq -nc \
  --arg eventId "$REPLAY_EVENT_ID" \
  --arg occurredAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg hotelName "Replay Manual Check Hotel" \
  --arg city "Singapore" \
  --argjson hotelId "$REPLAY_HOTEL_ID" \
  --argjson schemaVersion 1 \
  --arg eventType "CREATED" \
  --argjson pricePerNight 275 \
  '{eventId: $eventId, schemaVersion: $schemaVersion, eventType: $eventType, hotelId: $hotelId, hotelName: $hotelName, city: $city, pricePerNight: $pricePerNight, occurredAt: $occurredAt}')"

log "Replaying dead-letter #$DEAD_LETTER_ID through the admin retry endpoint with a corrected payload"
RETRY_RESPONSE="$(request POST "/api/kafka/dead-letters/$DEAD_LETTER_ID/retry" "{\"payloadOverride\":$(printf '%s' "$REPLAY_PAYLOAD" | jq -Rs .)}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$RETRY_RESPONSE" "200"
printf '%s' "$RETRY_RESPONSE" | json_body | jq

log "Waiting for the replayed event to be consumed successfully"
wait_for_replayed_event "$REPLAY_EVENT_ID"

log "Waiting for the dead-letter record to become RESOLVED"
wait_for_dead_letter_resolution "$DEAD_LETTER_ID"

log "Kafka retries + DLT manual check completed successfully"