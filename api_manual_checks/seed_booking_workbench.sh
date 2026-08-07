#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
RUN_ID="${RUN_ID:-$(date +%s)}"
ADMIN_USERNAME="booking_admin_${RUN_ID}"
ADMIN_EMAIL="${ADMIN_USERNAME}@test.com"
USER_USERNAME="booking_user_${RUN_ID}"
USER_EMAIL="${USER_USERNAME}@test.com"
PASSWORD="${PASSWORD:-password123}"

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

days_from_now() {
  date -v+"$1"d "+%F"
}

next_day() {
  date -j -v+1d -f "%F" "$1" "+%F"
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

register_user() {
  local username="$1"
  local email="$2"
  local response

  response="$(request POST "/api/v1/auth/register" "{\"username\":\"$username\",\"email\":\"$email\",\"password\":\"$PASSWORD\"}")"
  expect_status "$response" "201"
}

login_user() {
  local username="$1"
  local response

  response="$(request POST "/api/v1/auth/login" "{\"username\":\"$username\",\"password\":\"$PASSWORD\"}")"
  expect_status "$response" "200"
  printf '%s' "$response" | json_body | jq -r '.accessToken'
}

promote_admin() {
  local username="$1"

  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "update users set role = 'ADMIN' where username = '$username';" >/dev/null
}

create_hotel() {
  local token="$1"
  local name="$2"
  local city="$3"
  local price="$4"
  local response

  response="$(request POST "/api/v1/hotels" "{\"name\":\"$name\",\"city\":\"$city\",\"pricePerNight\":$price}" -H "Authorization: Bearer $token")"
  expect_status "$response" "201"
  printf '%s' "$response" | json_body | jq -r '.id'
}

create_inventory() {
  local token="$1"
  local hotel_id="$2"
  local room_type="$3"
  local date_value="$4"
  local available_rooms="$5"
  local response

  response="$(request POST "/api/v1/inventory" "{\"hotelId\":$hotel_id,\"roomType\":\"$room_type\",\"date\":\"$date_value\",\"availableRooms\":$available_rooms}" -H "Authorization: Bearer $token")"
  expect_status "$response" "201"
}

create_booking() {
  local token="$1"
  local hotel_id="$2"
  local room_type="$3"
  local check_in="$4"
  local check_out="$5"
  local payment_token="$6"
  local response

  response="$(request POST "/api/v1/bookings" "{\"hotelId\":$hotel_id,\"roomType\":\"$room_type\",\"checkIn\":\"$check_in\",\"checkOut\":\"$check_out\",\"paymentToken\":\"$payment_token\"}" -H "Authorization: Bearer $token")"
  expect_status "$response" "201"
  printf '%s' "$response" | json_body
}

cancel_booking() {
  local token="$1"
  local booking_id="$2"
  local response

  response="$(request DELETE "/api/v1/bookings/$booking_id" "" -H "Authorization: Bearer $token")"
  expect_status "$response" "200"
}

require_command curl
require_command jq
require_command docker

log "Checking application health at $BASE_URL"
HEALTH_RESPONSE="$(request GET "/actuator/health" "")"
expect_status "$HEALTH_RESPONSE" "200"
printf '%s' "$HEALTH_RESPONSE" | json_body | jq

log "Registering UI seed users"
register_user "$ADMIN_USERNAME" "$ADMIN_EMAIL"
register_user "$USER_USERNAME" "$USER_EMAIL"

log "Promoting $ADMIN_USERNAME to ADMIN"
promote_admin "$ADMIN_USERNAME"

log "Logging in as seeded users"
ADMIN_TOKEN="$(login_user "$ADMIN_USERNAME")"
USER_TOKEN="$(login_user "$USER_USERNAME")"

TODAY="$(date +%F)"
CHECK_IN_ONE="$(days_from_now 2)"
CHECK_OUT_ONE="$(days_from_now 4)"
CHECK_IN_TWO="$(days_from_now 5)"
CHECK_OUT_TWO="$(days_from_now 7)"
CHECK_IN_THREE="$(days_from_now 8)"
CHECK_OUT_THREE="$(days_from_now 10)"

log "Creating demo hotels"
TOKYO_HOTEL_ID="$(create_hotel "$ADMIN_TOKEN" "Voyage Tokyo Atelier" "Tokyo" 245)"
LISBON_HOTEL_ID="$(create_hotel "$ADMIN_TOKEN" "Voyage Lisbon Garden" "Lisbon" 185)"
COPENHAGEN_HOTEL_ID="$(create_hotel "$ADMIN_TOKEN" "Voyage Copenhagen Harbor" "Copenhagen" 205)"

log "Creating inventory windows for seeded hotels"
for date_value in "$CHECK_IN_ONE" "$(next_day "$CHECK_IN_ONE")"; do
  create_inventory "$ADMIN_TOKEN" "$TOKYO_HOTEL_ID" "DOUBLE" "$date_value" 3
done
for date_value in "$CHECK_IN_TWO" "$(next_day "$CHECK_IN_TWO")"; do
  create_inventory "$ADMIN_TOKEN" "$LISBON_HOTEL_ID" "SUITE" "$date_value" 2
done
for date_value in "$CHECK_IN_THREE" "$(next_day "$CHECK_IN_THREE")"; do
  create_inventory "$ADMIN_TOKEN" "$COPENHAGEN_HOTEL_ID" "SINGLE" "$date_value" 4
done

log "Creating two bookings and cancelling one so the UI shows both states"
ACTIVE_BOOKING_JSON="$(create_booking "$USER_TOKEN" "$TOKYO_HOTEL_ID" "DOUBLE" "$CHECK_IN_ONE" "$CHECK_OUT_ONE" "approve")"
CANCELLED_BOOKING_JSON="$(create_booking "$USER_TOKEN" "$LISBON_HOTEL_ID" "SUITE" "$CHECK_IN_TWO" "$CHECK_OUT_TWO" "approve")"
ACTIVE_BOOKING_ID="$(printf '%s' "$ACTIVE_BOOKING_JSON" | jq -r '.id')"
CANCELLED_BOOKING_ID="$(printf '%s' "$CANCELLED_BOOKING_JSON" | jq -r '.id')"
cancel_booking "$USER_TOKEN" "$CANCELLED_BOOKING_ID"

log "Snapshot of seeded bookings"
request GET "/api/v1/bookings" "" -H "Authorization: Bearer $USER_TOKEN" | json_body | jq

log "Snapshot of seeded notifications"
request GET "/api/v1/notifications/me" "" -H "Authorization: Bearer $USER_TOKEN" | json_body | jq

cat <<EOF

Seed complete.

Open:
  $BASE_URL/ui/bookings

Use these accounts in the UI:
  Admin user: $ADMIN_USERNAME
  Standard user: $USER_USERNAME
  Password: $PASSWORD

Seeded data:
  Hotels: $TOKYO_HOTEL_ID (Tokyo), $LISBON_HOTEL_ID (Lisbon), $COPENHAGEN_HOTEL_ID (Copenhagen)
  Active booking id: $ACTIVE_BOOKING_ID
  Cancelled booking id: $CANCELLED_BOOKING_ID
  Today: $TODAY

You can re-run this script after every application restart to repopulate the database.
EOF