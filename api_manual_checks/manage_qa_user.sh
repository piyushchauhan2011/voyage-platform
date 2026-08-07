#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-voyage-postgres}"
POSTGRES_USER="${POSTGRES_USER:-voyage}"
POSTGRES_DB="${POSTGRES_DB:-voyage_db}"
DEFAULT_PASSWORD="password123"
ACTION=""
USERNAME=""
EMAIL=""
PASSWORD="$DEFAULT_PASSWORD"
PROMOTE_AFTER_CREATE="false"

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

usage() {
  cat <<'EOF'
Usage:
  bash api_manual_checks/manage_qa_user.sh <action> [options]

Actions:
  create     Register a QA user through /api/v1/auth/register
  promote    Promote an existing user to ROLE_ADMIN in Postgres
  delete     Delete a user and any refresh tokens in Postgres

Options:
  --username <value>     Username to manage
  --email <value>        Email for create; defaults to <username>@test.com
  --password <value>     Password for create; defaults to password123
  --promote              Only valid with create; immediately promote the user to ADMIN
  --help                 Show this help text

Environment overrides:
  BASE_URL, POSTGRES_CONTAINER, POSTGRES_USER, POSTGRES_DB

Examples:
  bash api_manual_checks/manage_qa_user.sh create --username qa_user_1
  bash api_manual_checks/manage_qa_user.sh create --username qa_admin --promote
  bash api_manual_checks/manage_qa_user.sh promote --username qa_user_1
  bash api_manual_checks/manage_qa_user.sh delete --username qa_user_1
EOF
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

  if [[ -n "$body" ]]; then
    curl -sS -i -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" \
      -d "$body"
  else
    curl -sS -i -X "$method" "$BASE_URL$path"
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

username_required() {
  if [[ -z "$USERNAME" ]]; then
    fail "--username is required for action '$ACTION'"
  fi
}

create_user() {
  username_required

  if [[ -z "$EMAIL" ]]; then
    EMAIL="${USERNAME}@test.com"
  fi

  log "Registering QA user '$USERNAME' via $BASE_URL/api/v1/auth/register"
  local response
  response="$(request POST "/api/v1/auth/register" "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
  expect_status "$response" "201"
  printf '%s' "$response" | json_body | jq

  if [[ "$PROMOTE_AFTER_CREATE" == "true" ]]; then
    promote_user
  fi
}

promote_user() {
  username_required

  log "Promoting '$USERNAME' to ADMIN in Postgres"
  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "update users set role = 'ADMIN' where username = '$(sql_escape "$USERNAME")';" >/dev/null
}

delete_user() {
  username_required

  log "Deleting refresh tokens for '$USERNAME'"
  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "delete from refresh_tokens where user_id in (select id from users where username = '$(sql_escape "$USERNAME")');" >/dev/null

  log "Deleting user '$USERNAME'"
  docker exec -i "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "delete from users where username = '$(sql_escape "$USERNAME")';" >/dev/null
}

require_command curl
require_command jq
require_command docker

if (($# == 0)); then
  usage
  exit 1
fi

if [[ "$1" == "--help" ]]; then
  usage
  exit 0
fi

ACTION="$1"
shift

while (($# > 0)); do
  case "$1" in
    --username)
      USERNAME="$2"
      shift 2
      ;;
    --email)
      EMAIL="$2"
      shift 2
      ;;
    --password)
      PASSWORD="$2"
      shift 2
      ;;
    --promote)
      PROMOTE_AFTER_CREATE="true"
      shift
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

case "$ACTION" in
  create)
    create_user
    ;;
  promote)
    promote_user
    ;;
  delete)
    delete_user
    ;;
  *)
    fail "Unknown action: $ACTION"
    ;;
esac

log "QA user action '$ACTION' completed successfully"