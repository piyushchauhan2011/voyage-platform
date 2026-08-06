# Voyage App API Manual Checks

This folder gives a new developer or tester two ways to verify the API:

1. Manual requests with `curl` and `jq`
2. A runnable bash script that exercises the same flow end to end

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

## Security model to test

- `POST /api/auth/register` is public
- `POST /api/auth/login` is public
- `POST /api/auth/refresh` is public
- `POST /api/auth/logout` is public
- `GET /api/hotels/**` is public
- `POST /api/hotels` requires `ROLE_ADMIN`
- `PUT /api/hotels/{id}` requires `ROLE_ADMIN`
- `DELETE /api/hotels/{id}` requires `ROLE_ADMIN`

Important detail: registering a user always creates `ROLE_USER`, not `ROLE_ADMIN`.
To test write endpoints successfully, promote a user in Postgres.

## Manual flow

Set a base URL:

```bash
BASE_URL=http://localhost:8080
```

### 1. Register a user

```bash
curl -s -X POST "$BASE_URL/api/auth/register" \
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
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"qa_user","password":"password123"}')

echo "$LOGIN_RESPONSE" | jq

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refreshToken')
```

Expected status: `200`

### 3. Verify public hotel read endpoints

```bash
curl -s "$BASE_URL/api/hotels" | jq
curl -s "$BASE_URL/api/hotels/search?city=Tokyo" | jq
```

Expected status: `200`

### 4. Verify a normal user cannot create hotels

```bash
curl -i -s -X POST "$BASE_URL/api/hotels" \
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
ADMIN_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
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
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/hotels" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Grand Hyatt Manual Check","city":"Tokyo","pricePerNight":220}')

echo "$CREATE_RESPONSE" | jq

HOTEL_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')
```

Expected status: `201`

### 8. Update the hotel as admin

```bash
curl -s -X PUT "$BASE_URL/api/hotels/$HOTEL_ID" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Grand Hyatt Manual Check Updated","city":"Tokyo","pricePerNight":260}' | jq
```

Expected status: `200`

### 9. Delete the hotel as admin

```bash
curl -i -s -X DELETE "$BASE_URL/api/hotels/$HOTEL_ID" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN"
```

Expected status: `204`

### 10. Refresh the access token

```bash
REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}")

echo "$REFRESH_RESPONSE" | jq
```

Expected status: `200`

### 11. Logout and verify refresh token is revoked

```bash
curl -i -s -X POST "$BASE_URL/api/auth/logout" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}"
```

Expected status: `204`

Now retry refresh:

```bash
curl -i -s -X POST "$BASE_URL/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}"
```

Expected status: `401`

## Scripted flow

Run the script from the repo root:

```bash
bash api_manual_checks/run_auth_hotel_flow.sh
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
- `ROLE_USER` receives `403` on hotel create
- promoted admin can create, update, and delete a hotel
- refresh returns a new access token
- logout revokes the refresh token

## Troubleshooting

If registration returns `409`, the username or email already exists. Change the username/email or restart the app because the schema is recreated on startup.

If admin write calls still return `403`, log in again after promoting the user. Existing JWTs keep the old role claim.

If refresh returns `401` before logout, confirm you are using the refresh token value, not the access token.