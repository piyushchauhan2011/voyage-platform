# Voyage App API Manual Checks

This folder gives a new developer or tester two ways to verify the API and the Phase 5 Kafka flow:

1. Manual requests with `curl` and `jq`
2. A runnable bash script that exercises the same flow end to end

There is also a helper script for managing QA users outside the main end-to-end flow.
There is now a separate Kafka hardening script focused on retries, dead-letter handling, and replay.
There is now a separate Redis verification script focused on cache population, TTL checks, playground structures, pub/sub, and locks.
There is now a Postgres lab seed script (`seed_postgres_lab.sh`) that seeds volume data and runs an `EXPLAIN` via `/api/postgres/playground` (see also `postgres-lab/` SQL scripts and `/ui/postgres`).
There is now a RabbitMQ lab seed script (`seed_rabbitmq_lab.sh`) that sets up topology, publishes a job, and waits for a consumer delivery via `/api/rabbitmq/playground` (see also `rabbitmq-lab/` and `/ui/rabbitmq`).
There is now a Spring AI lab seed script (`seed_ai_lab.sh`) that seeds the hotel catalog, embeds it into pgvector, and walks similarity search, RAG, and the tool-calling agent via `/api/ai/playground` (see also `ai-lab/` and `/ui/ai`). It needs a `GEMINI_API_KEY` exported into the shell that runs the app.

## Prerequisites

- `docker`
- `curl`
- `jq`
- Java 21+

## Start the app

From the repo root:

```bash
docker compose up -d
./mvnw spring-boot:run -pl voyage-app
```

Wait for the app to be healthy:

```bash
curl -s http://localhost:8080/actuator/health | jq
```

Expected response:

```json
{
  "status": "UP"
}
```

Optional but useful during Phase 5:

- Open `http://localhost:8080/ui/kafka`
- The page lets you log in, trigger hotel writes, and watch consumed Kafka events appear from the app-side status feed
- Open `http://localhost:8080/ui/kafka/history` to inspect processed vs dead-lettered records separately
- The history page now lets an admin paste a bearer token and retry dead-letter records from the UI, optionally with an edited payload
- Admin users can also publish a raw Kafka payload through `/api/kafka/publish-raw` for deterministic DLT testing

Optional but useful during Phase 6:

- Run `bash api_manual_checks/run_redis_flow.sh`
- The script creates an admin-capable QA user, populates the hotel caches, verifies Redis keys through the `voyage-redis` container, and exercises the Redis playground endpoints
- The script assumes Docker Compose is running with the default `voyage-redis` and `voyage-postgres` container names

## Security model to test

- `POST /api/v1/auth/register` is public (creates `ROLE_CUSTOMER`)
- `POST /api/v1/auth/login` is public
- `POST /api/v1/auth/refresh` is public
- `POST /api/v1/auth/logout` is public
- `GET /api/v1/hotels/**` is public
- Hotel / inventory **writes** require `ROLE_ADMIN` or `ROLE_HOTEL_MANAGER` (ABAC then checks ownership + SaaS plan)
- `PATCH /api/v1/hotels/{id}/management` requires `ROLE_ADMIN` (assign manager / set `FREE|PRO|ENTERPRISE`)
- `/api/v1/payments/**` requires authentication (view/refund gated by ownership, rate plan, SaaS plan)
- `GET /ui/kafka` is public
- `GET /ui/kafka/status` is public and returns recently processed Kafka events
- `GET /ui/kafka/history` is public and shows the full processed and dead-letter event history
- `GET /ui/kafka/dead-letters` is public and returns recent dead-letter records

Important detail: registering a user always creates `ROLE_CUSTOMER`. Promote to `HOTEL_MANAGER` or `ADMIN` in Postgres (or via `manage_qa_user.sh --role ...`).

ABAC + payments demo:

```bash
bash api_manual_checks/run_abac_payment_flow.sh
```

## Manual flow

Set a base URL:

```bash
BASE_URL=http://localhost:8080
```

### 1. Register a user

```bash
curl -s -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"qa_user","email":"qa_user@test.com","password":"password123"}' | jq
```

Expected status: `201`

Expected JSON shape:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

### 2. Login and capture tokens

```bash
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"qa_user","password":"password123"}')

echo "$LOGIN_RESPONSE" | jq

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refreshToken')
```

Expected status: `200`

### 3. Verify public hotel read endpoints

```bash
curl -s "$BASE_URL/api/v1/hotels" | jq
curl -s "$BASE_URL/api/v1/hotels/search?city=Tokyo" | jq
```

Expected status: `200`

### 4. Verify a normal user cannot create hotels

```bash
curl -i -s -X POST "$BASE_URL/api/v1/hotels" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Forbidden Hotel","city":"Paris","pricePerNight":180}'
```

Expected status: `403`

### 5. Promote the user to admin in Postgres

If you are using the default Docker container from this repo:

```bash
docker exec -i voyage-postgres psql -U voyage -d voyage_db \
  -c "update users set role = 'ADMIN' where username = 'qa_user';"
```

If your compose env differs from the defaults, replace the DB name and username accordingly.

### 6. Login again to get an admin token

```bash
ADMIN_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"qa_user","password":"password123"}')

echo "$ADMIN_LOGIN_RESPONSE" | jq

ADMIN_ACCESS_TOKEN=$(echo "$ADMIN_LOGIN_RESPONSE" | jq -r '.accessToken')
ADMIN_REFRESH_TOKEN=$(echo "$ADMIN_LOGIN_RESPONSE" | jq -r '.refreshToken')
```

Expected status: `200`

Important detail: each login issues a new refresh token and revokes the previous one. Use `ADMIN_REFRESH_TOKEN` for the refresh and logout steps below.

### 7. Create a hotel as admin

```bash
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/hotels" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Grand Hyatt Manual Check","city":"Tokyo","pricePerNight":220}')

echo "$CREATE_RESPONSE" | jq

HOTEL_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')
```

Expected status: `201`

### 7a. Verify the create event reached the consumer feed

```bash
curl -s "$BASE_URL/ui/kafka/status" | jq
```

Expected: the most recent event list should include a `CREATED` event for the hotel id from step 7.

Useful fields to inspect:

```json
{
  "eventType": "CREATED",
  "hotelId": 123,
  "topicName": "hotel-events",
  "messageKey": "123",
  "partitionId": 0,
  "kafkaOffset": 5
}
```

### 8. Update the hotel as admin

```bash
curl -s -X PUT "$BASE_URL/api/v1/hotels/$HOTEL_ID" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Grand Hyatt Manual Check Updated","city":"Tokyo","pricePerNight":260}' | jq
```

Expected status: `200`

### 8a. Verify the update event reached the consumer feed

```bash
curl -s "$BASE_URL/ui/kafka/status" | jq
```

Expected: the feed now includes an `UPDATED` event for the same hotel id with a newer offset.

### 9. Delete the hotel as admin

```bash
curl -i -s -X DELETE "$BASE_URL/api/v1/hotels/$HOTEL_ID" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN"
```

Expected status: `204`

### 9a. Verify the delete event reached the consumer feed

```bash
curl -s "$BASE_URL/ui/kafka/status" | jq
```

Expected: the feed now includes a `DELETED` event for that hotel id.

### 9b. Optional hardening check: force a dead-letter record

This is useful only if you want to see retry exhaustion and dead-letter handling in action.

```bash
curl -i -s -X POST "$BASE_URL/api/kafka/publish-raw" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"messageKey":"broken-manual-1","payload":"BROKEN_EVENT_MANUAL"}'
```

Then inspect:

```bash
curl -s "$BASE_URL/ui/kafka/dead-letters" | jq
```

Expected: a new entry appears with the original topic, dead-letter topic, offset, payload, and `retryStatus` of `PENDING`.

### 9c. Retry the dead-letter record from the UI with a corrected payload

1. Open `http://localhost:8080/ui/kafka/history`
2. Paste an admin bearer token into the retry section at the top of the dead-letter table
3. Find the dead-letter row for your malformed payload
4. Replace the payload override text area with valid `HotelEvent` JSON, for example:

```json
{
  "eventId": "manual-replay-1",
  "schemaVersion": 1,
  "eventType": "CREATED",
  "hotelId": 900001,
  "hotelName": "Replay Manual Check Hotel",
  "city": "Singapore",
  "pricePerNight": 275,
  "occurredAt": "2026-08-06T12:00:00Z"
}
```

5. Click `Retry from UI`
6. Refresh the page if needed

Expected result:

- the processed-event history now contains the replayed event id
- the dead-letter row shows `RETRIED`, then `RESOLVED`
- `retryCount` increments

Important detail: if you retry with another invalid payload, the message will fail again and can return to the dead-letter flow.

### 10. Refresh the access token

```bash
REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}")

echo "$REFRESH_RESPONSE" | jq
```

Expected status: `200`

### 11. Logout and verify refresh token is revoked

```bash
curl -i -s -X POST "$BASE_URL/api/v1/auth/logout" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}"
```

Expected status: `204`

Now retry refresh:

```bash
curl -i -s -X POST "$BASE_URL/api/v1/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}"
```

Expected status: `401`

## Scripted flow

Run the script from the repo root:

```bash
bash api_manual_checks/run_auth_hotel_flow.sh
```

## Kafka retries + DLT script

If you want a dedicated scripted check for Kafka retries, the dead-letter topic, and replay resolution, run:

```bash
bash api_manual_checks/run_kafka_dlt_flow.sh
```

What this script does:

- checks the app health endpoint
- registers a fresh QA user
- promotes that user to `ROLE_ADMIN`
- logs in again to get an admin token
- publishes a malformed payload into `hotel-events`
- waits for the message to appear in `/ui/kafka/dead-letters`
- captures the dead-letter id
- retries that dead-letter through `/api/kafka/dead-letters/{id}/retry` with a corrected `HotelEvent` payload
- waits for the replayed event to appear in `/ui/kafka/status`
- waits for the dead-letter record to become `RESOLVED`

Optional environment overrides:

```bash
BASE_URL=http://localhost:8080 \
POSTGRES_CONTAINER=voyage-postgres \
POSTGRES_USER=voyage \
POSTGRES_DB=voyage_db \
KAFKA_TOPIC=hotel-events \
bash api_manual_checks/run_kafka_dlt_flow.sh
```

## QA user helper script

If you want to create, promote, or delete QA users without running the full hotel/Kafka flow, use:

```bash
bash api_manual_checks/manage_qa_user.sh <action> [options]
```

Supported actions:

- `create` registers a QA user through `POST /api/v1/auth/register`
- `promote` updates the user role in Postgres (default `ADMIN`; pass `--role HOTEL_MANAGER` or `CUSTOMER`)
- `delete` removes the user and any refresh tokens in Postgres

Common examples:

```bash
# Create a normal QA user
bash api_manual_checks/manage_qa_user.sh create --username qa_user_1

# Create and immediately promote to admin
bash api_manual_checks/manage_qa_user.sh create --username qa_admin_1 --promote

# Promote an existing user later
bash api_manual_checks/manage_qa_user.sh promote --username qa_user_1

# Delete a QA user cleanly
bash api_manual_checks/manage_qa_user.sh delete --username qa_user_1
```

Optional flags:

- `--email <value>` overrides the default `<username>@test.com`
- `--password <value>` overrides the default `password123`
- `--promote` only applies to the `create` action

The same environment overrides used by the main script also apply here:

```bash
BASE_URL=http://localhost:8080 \
POSTGRES_CONTAINER=voyage-postgres \
POSTGRES_USER=voyage \
POSTGRES_DB=voyage_db \
bash api_manual_checks/manage_qa_user.sh create --username qa_user_2
```

Optional environment overrides:

```bash
BASE_URL=http://localhost:8080 \
POSTGRES_CONTAINER=voyage-postgres \
POSTGRES_USER=voyage \
POSTGRES_DB=voyage_db \
bash api_manual_checks/run_auth_hotel_flow.sh
```

What the script verifies:

- health endpoint is up
- registration works
- login returns access + refresh tokens
- hotel reads are public
- `ROLE_CUSTOMER` receives `403` on hotel create
- promoted admin can create, update, and delete a hotel
- Kafka consumer status records `CREATED`, `UPDATED`, and `DELETED` hotel events
- exhausted consumer failures are forwarded to the dead-letter topic and appear in the dead-letter status feed
- refresh returns a new access token
- logout revokes the refresh token

For ABAC (manager ownership, SaaS plans, rate plans, mock refunds):

```bash
bash api_manual_checks/run_abac_payment_flow.sh
```

## Troubleshooting

If registration returns `409`, the username or email already exists. Change the username/email or restart the app because the schema is recreated on startup.

If admin write calls still return `403`, log in again after promoting the user. Existing JWTs keep the old role claim.

If refresh returns `401` before logout, confirm you are using the refresh token value, not the access token.

If the Kafka status feed stays empty after an admin hotel write, confirm Kafka is running from `docker compose up -d` and check `GET /ui/kafka/status` directly. The app only records events after the Kafka consumer receives them.