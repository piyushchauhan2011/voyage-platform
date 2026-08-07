#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
REDIS_CONTAINER="${REDIS_CONTAINER:-voyage-redis}"
RUN_ID="$(date +%s)"
USERNAME="redis_admin_${RUN_ID}"
EMAIL="${USERNAME}@test.com"
PASSWORD="password123"

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
      curl -sS -i -X "$method" "$BASE_URL$path" "${extra_args[@]}"
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

redis_exec() {
  docker exec "$REDIS_CONTAINER" redis-cli "$@"
}

wait_for_pubsub_message() {
  local expected_message="$1"
  local attempts=20
  local response=""

  while ((attempts > 0)); do
    response="$(curl -sS "$BASE_URL/api/redis/playground/pubsub/messages" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
    if printf '%s' "$response" | jq -e --arg message "$expected_message" 'map(select(.payload == $message)) | length > 0' >/dev/null; then
      printf '%s' "$response" | jq
      return 0
    fi
    attempts=$((attempts - 1))
    if ((attempts == 0)); then
      printf '%s\n' "$response" | jq || true
      fail "Timed out waiting for pub/sub message '$expected_message'"
    fi
    sleep 1
  done
}

require_command curl
require_command jq
require_command docker

log "Checking application health"
HEALTH_RESPONSE="$(request GET "/actuator/health" "")"
expect_status "$HEALTH_RESPONSE" "200"
printf '%s' "$HEALTH_RESPONSE" | json_body | jq

log "Checking Redis container health"
redis_exec ping | grep -q PONG || fail "Redis container did not respond to PING"

log "Registering a QA user for Redis checks"
REGISTER_RESPONSE="$(request POST "/api/v1/auth/register" "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
expect_status "$REGISTER_RESPONSE" "201"

log "Promoting $USERNAME to ADMIN"
docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "update users set role = 'ADMIN' where username = '$USERNAME';" >/dev/null

log "Logging in as ADMIN"
LOGIN_RESPONSE="$(request POST "/api/v1/auth/login" "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
expect_status "$LOGIN_RESPONSE" "200"
LOGIN_JSON="$(printf '%s' "$LOGIN_RESPONSE" | json_body)"
ADMIN_ACCESS_TOKEN="$(printf '%s' "$LOGIN_JSON" | jq -r '.accessToken')"

log "Flushing Redis so the cache checks start clean"
redis_exec FLUSHDB >/dev/null

log "Creating a hotel to drive Redis caching"
CREATE_RESPONSE="$(request POST "/api/v1/hotels" "{\"name\":\"Redis Manual Check Hotel\",\"city\":\"Tokyo\",\"pricePerNight\":245}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$CREATE_RESPONSE" "201"
CREATE_JSON="$(printf '%s' "$CREATE_RESPONSE" | json_body)"
HOTEL_ID="$(printf '%s' "$CREATE_JSON" | jq -r '.id')"
printf '%s' "$CREATE_JSON" | jq

log "Calling hotel GET by id twice to populate Redis"
FIRST_GET_RESPONSE="$(request GET "/api/v1/hotels/$HOTEL_ID" "")"
SECOND_GET_RESPONSE="$(request GET "/api/v1/hotels/$HOTEL_ID" "")"
expect_status "$FIRST_GET_RESPONSE" "200"
expect_status "$SECOND_GET_RESPONSE" "200"

log "Verifying the hotel-by-id cache key exists in Redis"
redis_exec KEYS "hotelById::*" | grep -q "$HOTEL_ID" || fail "Expected hotelById cache key for hotel $HOTEL_ID"
redis_exec TTL "hotelById::$HOTEL_ID"

log "Calling hotel city search twice to populate Redis"
SEARCH_RESPONSE="$(request GET "/api/v1/hotels/search?city=Tokyo" "")"
expect_status "$SEARCH_RESPONSE" "200"
printf '%s' "$SEARCH_RESPONSE" | json_body | jq
redis_exec KEYS "hotelsByCity::*" | grep -q "Tokyo" || fail "Expected city cache key for Tokyo"

log "Exercising Redis playground String"
STRING_RESPONSE="$(request POST "/api/redis/playground/strings/demo:hotel:$HOTEL_ID" "{\"value\":\"available\",\"ttlSeconds\":120}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$STRING_RESPONSE" "200"
printf '%s' "$STRING_RESPONSE" | json_body | jq

log "Exercising Redis playground Hash"
HASH_RESPONSE="$(request POST "/api/redis/playground/hashes/demo:hotel:$HOTEL_ID" "{\"fields\":{\"name\":\"Redis Manual Check Hotel\",\"city\":\"Tokyo\",\"pricePerNight\":\"245\"},\"ttlSeconds\":180}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$HASH_RESPONSE" "200"
printf '%s' "$HASH_RESPONSE" | json_body | jq

log "Exercising Redis playground List"
LIST_RESPONSE="$(request POST "/api/redis/playground/lists/demo:recent-searches" "{\"values\":[\"Tokyo\",\"Paris\",\"Dubai\"],\"ttlSeconds\":180}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$LIST_RESPONSE" "200"
printf '%s' "$LIST_RESPONSE" | json_body | jq

log "Exercising Redis playground Set"
SET_RESPONSE="$(request POST "/api/redis/playground/sets/demo:watchers" "{\"values\":[\"alice\",\"bob\",\"alice\"],\"ttlSeconds\":180}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$SET_RESPONSE" "200"
printf '%s' "$SET_RESPONSE" | json_body | jq

log "Exercising Redis playground Sorted Set"
SORTED_SET_RESPONSE="$(request POST "/api/redis/playground/sorted-sets/demo:popularity" "{\"values\":[{\"value\":\"tokyo\",\"score\":98},{\"value\":\"paris\",\"score\":87}],\"ttlSeconds\":180}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$SORTED_SET_RESPONSE" "200"
printf '%s' "$SORTED_SET_RESPONSE" | json_body | jq

log "Verifying a playground TTL lookup"
TTL_RESPONSE="$(request GET "/api/redis/playground/ttl/demo:hotel:$HOTEL_ID" "" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$TTL_RESPONSE" "200"
printf '%s' "$TTL_RESPONSE" | json_body | jq

PUBSUB_MESSAGE="redis-manual-check-$RUN_ID"
log "Publishing a Redis pub/sub message"
PUBLISH_RESPONSE="$(request POST "/api/redis/playground/pubsub/publish" "{\"message\":\"$PUBSUB_MESSAGE\"}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$PUBLISH_RESPONSE" "200"
printf '%s' "$PUBLISH_RESPONSE" | json_body | jq

log "Waiting for the application-side pub/sub recorder to observe the message"
wait_for_pubsub_message "$PUBSUB_MESSAGE"

log "Acquiring a Redis lock"
LOCK_RESPONSE="$(request POST "/api/redis/playground/locks/inventory:hotel-$HOTEL_ID/acquire" "{\"owner\":\"manual-check-1\",\"ttlSeconds\":30}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$LOCK_RESPONSE" "200"
printf '%s' "$LOCK_RESPONSE" | json_body | jq

log "Confirming a second lock owner cannot acquire the same lock"
SECOND_LOCK_RESPONSE="$(request POST "/api/redis/playground/locks/inventory:hotel-$HOTEL_ID/acquire" "{\"owner\":\"manual-check-2\",\"ttlSeconds\":30}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$SECOND_LOCK_RESPONSE" "200"
printf '%s' "$SECOND_LOCK_RESPONSE" | json_body | jq
printf '%s' "$SECOND_LOCK_RESPONSE" | json_body | jq -e '.acquired == false' >/dev/null || fail "Expected second lock acquisition to fail"

log "Releasing the Redis lock"
RELEASE_RESPONSE="$(request DELETE "/api/redis/playground/locks/inventory:hotel-$HOTEL_ID?owner=manual-check-1" "" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$RELEASE_RESPONSE" "200"
printf '%s' "$RELEASE_RESPONSE" | json_body | jq

log "Redis manual verification flow completed successfully"