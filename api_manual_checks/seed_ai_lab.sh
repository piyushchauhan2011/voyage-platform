#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
RUN_ID="${RUN_ID:-$(date +%s)}"
ADMIN_USERNAME="ai_lab_admin_${RUN_ID}"
ADMIN_EMAIL="${ADMIN_USERNAME}@test.com"
PASSWORD="${PASSWORD:-password123}"
CONVERSATION_ID="ai_lab_${RUN_ID}"

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

# Checked before anything else: every rung past seed-catalog calls Gemini, and a missing
# key here is the single most common reason this script appears to break.
log "Checking Gemini configuration"
response="$(request GET "/api/ai/playground/status" "" -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{apiKeyConfigured, chatModel, embeddingModel, topK, similarityThreshold}')"

if [[ "$(printf '%s' "$response" | json_body | jq -r '.apiKeyConfigured')" != "true" ]]; then
  cat >&2 <<'HINT'

No Gemini API key reached the app.

Docker Compose reads .env automatically; Spring Boot does not. Putting the key in
.env is not enough on its own — it has to be exported into the shell that runs the app:

    set -a && . ./.env && set +a
    ./mvnw spring-boot:run -pl voyage-app

Get a key from https://aistudio.google.com/apikey if you do not have one yet.
HINT
  fail "apiKeyConfigured is false — see the hint above"
fi

log "Rung 0 — seeding the hotel catalog (safe to re-run)"
response="$(request POST "/api/ai/playground/seed-catalog" "" -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{created, alreadyPresent, totalHotels, inventoryRowsCreated, observation}')"

log "Rung 2 — embedding two phrases and comparing them"
response="$(request POST "/api/ai/playground/embed" \
  '{"first":"a quiet room a short walk from the sand","second":"beachfront hotel near the sea"}' \
  -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{model, dimensions, similarity, similarityToUnrelatedText, tookMs}')"

log "Rung 3 — embedding every hotel into pgvector"
response="$(request POST "/api/ai/playground/ingest" "" -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{documentsIngested, embeddingModel, tookMs, observation}')"

# The payoff query: none of these hotels have "beach" in their name or city, so a SQL
# LIKE would return nothing at all.
log "Rung 4 — similarity search for 'somewhere near the sand and surf'"
response="$(request POST "/api/ai/playground/search" \
  '{"query":"somewhere near the sand and surf","topK":6}' \
  -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '[.matches[] | {score, name, city, pricePerNight}]')"

log "Rung 5 — RAG: retrieve, augment, generate"
response="$(request POST "/api/ai/playground/rag" \
  '{"question":"Find hotels near the beach under $100"}' \
  -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{answer, retrieved: [.retrievedDocuments[].name], tookMs}')"

log "Rung 7 — the agent: RAG for 'near beach', a tool call for 'under \$100'"
response="$(request POST "/api/ai/playground/assistant" \
  "{\"question\":\"Find hotels near beach under \$100\",\"conversationId\":\"$CONVERSATION_ID\"}" \
  -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{answer, toolCalls, retrieved: [.retrievedDocuments[].name], usage, tookMs}')"

if [[ "$(printf '%s' "$response" | json_body | jq '.toolCalls | length')" -eq 0 ]]; then
  echo "NOTE: the model answered without calling a tool this time — models are non-deterministic." >&2
fi

# Same conversation id, so "those" has to resolve from memory rather than the question.
log "Rung 7 — follow-up on the same conversation to exercise chat memory"
response="$(request POST "/api/ai/playground/assistant" \
  "{\"question\":\"Which of those has wifi?\",\"conversationId\":\"$CONVERSATION_ID\"}" \
  -H "Authorization: Bearer $TOKEN")"
expect_status "$response" "200"
printf '%s\n' "$(printf '%s' "$response" | json_body | jq '{answer, tookMs}')"

log "Done. Open http://localhost:8080/ui/ai to walk the same ladder in the browser."
printf 'Admin username: %s\n' "$ADMIN_USERNAME"
printf 'Password: %s\n' "$PASSWORD"
printf 'Conversation id: %s\n' "$CONVERSATION_ID"
