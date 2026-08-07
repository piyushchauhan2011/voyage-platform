#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
RUN_ID="$(date +%s)"
ADMIN_USER="qa_admin_${RUN_ID}"
MANAGER_USER="qa_mgr_${RUN_ID}"
CUSTOMER_USER="qa_cust_${RUN_ID}"
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
      curl -sS -i -X "$method" "$BASE_URL$path" -H "Content-Type: application/json" "${extra_args[@]}" -d "$body"
    else
      curl -sS -i -X "$method" "$BASE_URL$path" -H "Content-Type: application/json" -d "$body"
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

set_role() {
  local username="$1"
  local role="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "update users set role = '$role' where username = '$username';" >/dev/null
}

register_and_login() {
  local username="$1"
  local email="${username}@test.com"
  local response
  response="$(request POST "/api/v1/auth/register" "{\"username\":\"$username\",\"email\":\"$email\",\"password\":\"$PASSWORD\"}")"
  expect_status "$response" "201"
  response="$(request POST "/api/v1/auth/login" "{\"username\":\"$username\",\"password\":\"$PASSWORD\"}")"
  expect_status "$response" "200"
  printf '%s' "$response" | json_body | jq -r '.accessToken'
}

user_id() {
  local username="$1"
  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "select id from users where username = '$username';"
}

log "Checking service health at $BASE_URL"
expect_status "$(request GET "/actuator/health" "")" "200"

log "Registering admin / manager / customer"
ADMIN_TOKEN="$(register_and_login "$ADMIN_USER")"
MANAGER_TOKEN="$(register_and_login "$MANAGER_USER")"
CUSTOMER_TOKEN="$(register_and_login "$CUSTOMER_USER")"

set_role "$ADMIN_USER" "ADMIN"
set_role "$MANAGER_USER" "HOTEL_MANAGER"
ADMIN_TOKEN="$(request POST "/api/v1/auth/login" "{\"username\":\"$ADMIN_USER\",\"password\":\"$PASSWORD\"}" | json_body | jq -r '.accessToken')"
MANAGER_TOKEN="$(request POST "/api/v1/auth/login" "{\"username\":\"$MANAGER_USER\",\"password\":\"$PASSWORD\"}" | json_body | jq -r '.accessToken')"
MANAGER_ID="$(user_id "$MANAGER_USER")"

log "CUSTOMER cannot create hotels"
expect_status "$(request POST "/api/v1/hotels" "{\"name\":\"Nope\",\"city\":\"Paris\",\"pricePerNight\":100}" -H "Authorization: Bearer $CUSTOMER_TOKEN")" "403"

log "HOTEL_MANAGER creates first hotel (FREE plan)"
CREATE_RESPONSE="$(request POST "/api/v1/hotels" "{\"name\":\"ABAC Inn\",\"city\":\"Berlin\",\"pricePerNight\":200}" -H "Authorization: Bearer $MANAGER_TOKEN")"
expect_status "$CREATE_RESPONSE" "201"
HOTEL_JSON="$(printf '%s' "$CREATE_RESPONSE" | json_body)"
printf '%s' "$HOTEL_JSON" | jq
HOTEL_ID="$(printf '%s' "$HOTEL_JSON" | jq -r '.id')"

log "FREE plan blocks inventory writes"
INV_DATE="$(date -u -v+45d +%Y-%m-%d 2>/dev/null || date -u -d '+45 days' +%Y-%m-%d)"
expect_status "$(request POST "/api/v1/inventory" "{\"hotelId\":$HOTEL_ID,\"roomType\":\"DOUBLE\",\"date\":\"$INV_DATE\",\"availableRooms\":2}" -H "Authorization: Bearer $MANAGER_TOKEN")" "403"

log "Admin upgrades hotel to PRO and inventory succeeds"
expect_status "$(request PATCH "/api/v1/hotels/$HOTEL_ID/management" "{\"managerId\":$MANAGER_ID,\"saasPlan\":\"PRO\"}" -H "Authorization: Bearer $ADMIN_TOKEN")" "200"
expect_status "$(request POST "/api/v1/inventory" "{\"hotelId\":$HOTEL_ID,\"roomType\":\"DOUBLE\",\"date\":\"$INV_DATE\",\"availableRooms\":2}" -H "Authorization: Bearer $MANAGER_TOKEN")" "201"

CHECK_IN="$INV_DATE"
CHECK_OUT="$(date -u -v+46d +%Y-%m-%d 2>/dev/null || date -u -d '+46 days' +%Y-%m-%d)"

log "Customer books NON_REFUNDABLE (15% off) — self-refund forbidden"
BOOK_NR="$(request POST "/api/v1/bookings" "{\"hotelId\":$HOTEL_ID,\"roomType\":\"DOUBLE\",\"checkIn\":\"$CHECK_IN\",\"checkOut\":\"$CHECK_OUT\",\"paymentToken\":\"approve\",\"ratePlan\":\"NON_REFUNDABLE\"}" -H "Authorization: Bearer $CUSTOMER_TOKEN")"
expect_status "$BOOK_NR" "201"
BOOK_NR_JSON="$(printf '%s' "$BOOK_NR" | json_body)"
printf '%s' "$BOOK_NR_JSON" | jq
BOOKING_ID="$(printf '%s' "$BOOK_NR_JSON" | jq -r '.id')"
PAY_JSON="$(request GET "/api/v1/payments?bookingId=$BOOKING_ID" "" -H "Authorization: Bearer $CUSTOMER_TOKEN" | json_body)"
printf '%s' "$PAY_JSON" | jq
PAYMENT_ID="$(printf '%s' "$PAY_JSON" | jq -r '.id')"
expect_status "$(request POST "/api/v1/payments/$PAYMENT_ID/refund" "" -H "Authorization: Bearer $CUSTOMER_TOKEN")" "403"

log "PRO manager also cannot refund — upgrade to ENTERPRISE"
expect_status "$(request POST "/api/v1/payments/$PAYMENT_ID/refund" "" -H "Authorization: Bearer $MANAGER_TOKEN")" "403"
expect_status "$(request PATCH "/api/v1/hotels/$HOTEL_ID/management" "{\"managerId\":$MANAGER_ID,\"saasPlan\":\"ENTERPRISE\"}" -H "Authorization: Bearer $ADMIN_TOKEN")" "200"
expect_status "$(request POST "/api/v1/payments/$PAYMENT_ID/refund" "" -H "Authorization: Bearer $MANAGER_TOKEN")" "200"

log "ABAC + payment manual check completed successfully"
