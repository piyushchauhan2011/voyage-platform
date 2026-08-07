#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
RUN_ID="${RUN_ID:-$(date +%s)}"
ADMIN_USERNAME="postgres_lab_admin_${RUN_ID}"
ADMIN_EMAIL="${ADMIN_USERNAME}@test.com"
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

require_command curl
require_command jq
require_command docker

log "Registering admin user ${ADMIN_USERNAME}"
response="$(request POST "/api/v1/auth/register" "{\"username\":\"$ADMIN_USERNAME\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PASSWORD\"}")"
expect_status "$response" "201"

log "Promoting to ADMIN"
docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "update users set role = 'ADMIN' where username = '$ADMIN_USERNAME';" >/dev/null

log "Logging in"
response="$(request POST "/api/v1/auth/login" "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$PASSWORD\"}")"
expect_status "$response" "200"
TOKEN="$(printf '%s' "$response" | json_body | jq -r '.accessToken')"

log "Seeding Postgres lab volume data"
response="$(request POST "/api/postgres/playground/seed" "" -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq .)"

log "EXPLAIN booking_by_hotel"
response="$(request POST "/api/postgres/playground/explain" "{\"scenario\":\"booking_by_hotel\"}" -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{scenario, scanType, elapsedMs, sql}')"

log "Done. Open http://localhost:8080/ui/postgres or inspect plans in the full explain response."
printf 'Admin username: %s\n' "$ADMIN_USERNAME"
printf 'Password: %s\n' "$PASSWORD"
