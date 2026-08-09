# Elasticsearch Hotel Search Lab

Learn full-text hotel search with **Spring Data Elasticsearch**: Postgres is the source of truth, Elasticsearch is a derived search index, and Thai + English use different analyzers.

## Why not only Postgres?

| Approach | Good for | Weak at |
|---|---|---|
| JPA / SQL (`GET /api/v1/hotels?city=`) | Exact filters, joins, transactions | Ranked free-text, Thai segmentation |
| Elasticsearch (`GET /api/v1/search/hotels?q=`) | Analyzers, relevance, highlights | Being the system of record |
| pgvector (`/ui/ai`) | Semantic similarity ("near the sand") | Exact token search, ops simplicity |

## Start

```bash
# ES alone (Spring Boot 4.1 uses the Elasticsearch 9.x Java client — Compose must match)
docker compose --profile elasticsearch up -d

# Or full app deps (includes Elasticsearch)
docker compose --profile app up -d

./mvnw spring-boot:run -pl voyage-app
open http://localhost:8080/ui/search
```

If you previously ran Elasticsearch 8.x locally, wipe the old volume once after upgrading:

```bash
docker compose --profile elasticsearch down
docker volume rm voyage-platform_elasticsearch-data
docker compose --profile elasticsearch up -d
```

Toggle **ไทย** in the page header (`?lang=th`) — UI strings come from `messages_th.properties`, and search boosts Thai fields.

## Create an ADMIN user

Seed / reindex / explain need an ADMIN JWT. With the app and Postgres running:

```bash
# Register + promote to ADMIN (default password: password123)
bash api_manual_checks/manage_qa_user.sh create --username admin --promote
```

Log in from the terminal:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password123"}' | jq -r .accessToken)
echo "$TOKEN"
```

Or open http://localhost:8080/ui/search and use the admin panel with `admin` / `password123`.

## Lab path

1. Login as ADMIN in the playground panel (see above if you need to create the user).
2. **Seed hotels** — writes ~100 bilingual rows to Postgres (`name` + `nameTh`, etc.).
3. **Reindex** — builds the `hotels` index and bulk-loads documents.
4. Search `beach`, then `ชายหาด`. Same intent, different tokens / analyzers.
5. Call explain: `GET /api/search/playground/explain?q=ชายหาด&lang=th`

## Concepts to notice

- **Dual-write / reindex**: creating a hotel via the API also upserts ES when search is enabled; Seed only touches Postgres until you Reindex (or rely on per-row sync after creates through `HotelService`).
- **Analyzers**: English fields use the built-in `english` analyzer; Thai fields use built-in `thai` (segments without spaces).
- **Locale-biased `multi_match`**: `lang=th` boosts `nameTh` / `cityTh` / `descriptionTh`; `lang=en` boosts English fields.
- **HTMX fragment**: `/ui/search/results` returns a Thymeleaf partial — no client-side JSON templating required for the result list.

## API cheat sheet

```bash
# Public search
curl 'http://localhost:8080/api/v1/search/hotels?q=%E0%B8%8A%E0%B8%B2%E0%B8%A2%E0%B8%AB%E0%B8%B2%E0%B8%94&lang=th'

# Admin (Bearer token from /api/v1/auth/login)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/search/playground/seed?count=100'
curl -X POST -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/search/playground/reindex'
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/search/playground/status'
```

Helper script: `bash api_manual_checks/seed_search_lab.sh`

## Disable

```yaml
application:
  search:
    enabled: false
```

Unit tests already set this so CI does not need Elasticsearch.
