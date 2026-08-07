#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
RUN_ID="$(date +%s)"
USERNAME="qa_user_${RUN_ID}"
EMAIL="${USERNAME}@test.com"
PASSWORD="password123"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_command docker

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

wait_for_kafka_event() {
  local hotel_id="$1"
  local event_type="$2"
  local attempts=20
  local response=""

  while ((attempts > 0)); do
    response="$(curl -sS "$BASE_URL/ui/kafka/status")"
    if printf '%s' "$response" | jq -e --arg hotel_id "$hotel_id" --arg event_type "$event_type" '
      map(select((.hotelId | tostring) == $hotel_id and .eventType == $event_type)) | length > 0
    ' >/dev/null; then
      printf '%s' "$response" | jq
      return 0
    fi
    attempts=$((attempts - 1))
    if ((attempts == 0)); then
      printf '%s\n' "$response" | jq || true
      fail "Timed out waiting for Kafka event $event_type for hotel $hotel_id"
    fi
    sleep 1
  done
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

log "Checking service health at $BASE_URL"
HEALTH_RESPONSE="$(request GET "/actuator/health" "")"
expect_status "$HEALTH_RESPONSE" "200"
printf '%s' "$HEALTH_RESPONSE" | json_body | jq

log "Registering a new ROLE_USER account: $USERNAME"
REGISTER_RESPONSE="$(request POST "/api/v1/auth/register" "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
expect_status "$REGISTER_RESPONSE" "201"
printf '%s' "$REGISTER_RESPONSE" | json_body | jq

log "Logging in as the new user"
LOGIN_RESPONSE="$(request POST "/api/v1/auth/login" "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
expect_status "$LOGIN_RESPONSE" "200"
LOGIN_JSON="$(printf '%s' "$LOGIN_RESPONSE" | json_body)"
printf '%s' "$LOGIN_JSON" | jq
ACCESS_TOKEN="$(printf '%s' "$LOGIN_JSON" | jq -r '.accessToken')"
REFRESH_TOKEN="$(printf '%s' "$LOGIN_JSON" | jq -r '.refreshToken')"

log "Verifying public hotel reads"
GET_HOTELS_RESPONSE="$(request GET "/api/v1/hotels" "")"
expect_status "$GET_HOTELS_RESPONSE" "200"
printf '%s' "$GET_HOTELS_RESPONSE" | json_body | jq

log "Verifying ROLE_USER cannot create a hotel"
FORBIDDEN_CREATE_RESPONSE="$(request POST "/api/v1/hotels" "{\"name\":\"Forbidden Hotel\",\"city\":\"Paris\",\"pricePerNight\":180}" -H "Authorization: Bearer $ACCESS_TOKEN")"
expect_status "$FORBIDDEN_CREATE_RESPONSE" "403"
printf '%s' "$FORBIDDEN_CREATE_RESPONSE" | json_body | jq

log "Promoting $USERNAME to ADMIN inside Postgres"
docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "update users set role = 'ADMIN' where username = '$USERNAME';" >/dev/null

log "Logging in again to get a token with ADMIN role"
ADMIN_LOGIN_RESPONSE="$(request POST "/api/v1/auth/login" "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
expect_status "$ADMIN_LOGIN_RESPONSE" "200"
ADMIN_LOGIN_JSON="$(printf '%s' "$ADMIN_LOGIN_RESPONSE" | json_body)"
printf '%s' "$ADMIN_LOGIN_JSON" | jq
ADMIN_ACCESS_TOKEN="$(printf '%s' "$ADMIN_LOGIN_JSON" | jq -r '.accessToken')"
ADMIN_REFRESH_TOKEN="$(printf '%s' "$ADMIN_LOGIN_JSON" | jq -r '.refreshToken')"

log "Creating a hotel as admin"
CREATE_RESPONSE="$(request POST "/api/v1/hotels" "{\"name\":\"Grand Hyatt Manual Check\",\"city\":\"Tokyo\",\"pricePerNight\":220}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$CREATE_RESPONSE" "201"
CREATE_JSON="$(printf '%s' "$CREATE_RESPONSE" | json_body)"
printf '%s' "$CREATE_JSON" | jq
HOTEL_ID="$(printf '%s' "$CREATE_JSON" | jq -r '.id')"

log "Verifying the CREATED Kafka event appears in /ui/kafka/status"
wait_for_kafka_event "$HOTEL_ID" "CREATED"

log "Updating hotel $HOTEL_ID as admin"
UPDATE_RESPONSE="$(request PUT "/api/v1/hotels/$HOTEL_ID" "{\"name\":\"Grand Hyatt Manual Check Updated\",\"city\":\"Tokyo\",\"pricePerNight\":260}" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$UPDATE_RESPONSE" "200"
printf '%s' "$UPDATE_RESPONSE" | json_body | jq

log "Verifying the UPDATED Kafka event appears in /ui/kafka/status"
wait_for_kafka_event "$HOTEL_ID" "UPDATED"

log "Deleting hotel $HOTEL_ID as admin"
DELETE_RESPONSE="$(request DELETE "/api/v1/hotels/$HOTEL_ID" "" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")"
expect_status "$DELETE_RESPONSE" "204"

log "Verifying the DELETED Kafka event appears in /ui/kafka/status"
wait_for_kafka_event "$HOTEL_ID" "DELETED"

log "Refreshing the access token"
REFRESH_RESPONSE="$(request POST "/api/v1/auth/refresh" "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}")"
expect_status "$REFRESH_RESPONSE" "200"
printf '%s' "$REFRESH_RESPONSE" | json_body | jq

log "Logging out and revoking refresh tokens"
LOGOUT_RESPONSE="$(request POST "/api/v1/auth/logout" "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}")"
expect_status "$LOGOUT_RESPONSE" "204"

log "Verifying the revoked refresh token now returns 401"
REFRESH_AFTER_LOGOUT_RESPONSE="$(request POST "/api/v1/auth/refresh" "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}")"
expect_status "$REFRESH_AFTER_LOGOUT_RESPONSE" "401"
printf '%s' "$REFRESH_AFTER_LOGOUT_RESPONSE" | json_body | jq

log "API manual check flow completed successfully"