# Spring AI Skills Lab

Hands-on **Spring AI** for **chat**, **embeddings**, **vector databases**, **RAG**, **tool calling**, and **agents** — built up one rung at a time into a hotel assistant that answers:

> "Find hotels near beach under $100"

That question is the whole lesson. It is two questions in one coat:

| Part | Kind of question | Mechanism |
|---|---|---|
| "near beach" | fuzzy, subjective | embedding similarity over hotel descriptions |
| "under $100" | exact, arithmetic | a tool call that runs a SQL query |

Neither mechanism can do the other's job. Similarity search cannot compare numbers, and a `WHERE` clause cannot tell that "sand and surf" means beach. Deciding which to reach for, and combining the results, is what the agent is for.

## Prerequisites

1. Get a free Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey).
2. Add it to `.env` (already gitignored) as `GEMINI_API_KEY=...`.
3. Load `.env` into your shell, then start the app:

```bash
set -a && . ./.env && set +a     # see the note below — this step is easy to miss
docker compose --profile app up -d
./mvnw spring-boot:run -pl voyage-app
```

> **`.env` alone is not enough.** Docker Compose reads `.env` automatically; Spring Boot does not. `./mvnw spring-boot:run` only sees variables exported in your shell.
>
> This never mattered before Phase 8 because every other variable in `application.yml` has a default matching `.env` (`${POSTGRES_USER:voyage}` and friends), so the app worked either way. `GEMINI_API_KEY` is the first value with no usable default, so it is the first place the gap shows. If `status` reports `apiKeyConfigured: false`, this is why.

4. Open http://localhost:8080/ui/ai and log in as ADMIN.

The app still starts without a key — every other phase keeps working, and the AI endpoints return a clear 503 telling you what is missing. Check with **Status** in the UI or:

```bash
curl -s localhost:8080/api/ai/playground/status -H "Authorization: Bearer $TOKEN"
```

> Postgres runs the `pgvector/pgvector:pg16` image rather than plain `postgres:16-alpine`, because the vector store needs the `vector` extension. Same PG16 base, so your existing `postgres-data` volume carries over.

## The ladder

Work through these in order. Each one is a panel in the UI and an endpoint under `/api/ai/playground`.

| # | Rung | Endpoint | What to notice |
|---|---|---|---|
| 0 | Seed catalog | `POST /seed-catalog` | ~20 hotels with descriptions written so "beach" never appears literally |
| 1 | Chat | `POST /chat` | Ask for a Voyage hotel — the model invents one. This is the baseline failure |
| 1 | Prompt template | `POST /prompt-template` | Template and variables kept separate, so a prompt can be reviewed like code |
| 2 | Embedding | `POST /embed` | Two travel phrases score high against each other; an unrelated sentence scores low |
| 3 | Ingest | `POST /ingest` | Each hotel becomes a row of text + a 768-float vector + JSON metadata |
| 4 | Similarity search | `POST /search` | Pure retrieval, with scores. No prose to hide behind |
| 5 | RAG | `POST /rag` | Retrieved rows are pasted into the prompt, so answers cite real hotels |
| 6 | Tools | `GET /tools` | The JSON contract the model programs against |
| 7 | Assistant | `POST /assistant` | RAG + tools + memory, with a full trace |

### The moment worth waiting for

At rung 4, search for **"somewhere near the sand and surf"**. The top hits include hotels whose name and city contain neither "beach" nor "sand" — a SQL `LIKE` would return nothing. That is the case for embeddings in one query.

At rung 5, ask **"Find hotels near the beach under $100"** with no metadata filter. The model will often include a $320 resort, because "under $100" is arithmetic and similarity search cannot do arithmetic. That failure is the case for tool calling.

At rung 7, ask the same question. The assistant retrieves beach candidates by meaning, then calls `searchHotels(maxPrice=100)` to check them against the real database, and the trace shows both.

## Try it from bash

`api_manual_checks/seed_ai_lab.sh` runs the whole ladder in one go — it registers its own admin,
promotes it in Postgres, seeds the catalog, ingests into pgvector, then prints the trace for search,
RAG, and the agent (including which tool the model chose) before asking a follow-up on the same
conversation. It checks `apiKeyConfigured` up front and stops with instructions if the key never
reached the app.

```bash
bash api_manual_checks/seed_ai_lab.sh
```

## Try it with curl

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password123"}' | jq -r .accessToken)

curl -s -X POST localhost:8080/api/ai/playground/seed-catalog \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST localhost:8080/api/ai/playground/ingest \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST localhost:8080/api/ai/playground/search \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"query":"somewhere near the sand and surf","filterExpression":"pricePerNight < 100"}'

curl -s -X POST localhost:8080/api/ai/playground/assistant \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"Find hotels near beach under $100"}'
```

Follow-ups use chat memory, so pass the same `conversationId`:

```bash
curl -s -X POST localhost:8080/api/ai/playground/assistant \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"Which of those has wifi?","conversationId":"voyage-ai-lab"}'
```

## Code map

| Class | Role |
|---|---|
| `AiLabProperties` | Reads `application.ai.*`; decides whether a usable key is present |
| `HotelCatalogSeeder` | ~20 hotels plus 30 days of room inventory |
| `AiPlaygroundService` | Rungs 1–2: chat, prompt templates, embeddings, cosine similarity |
| `HotelDocumentIngestor` | Rung 3: hotels to `Document`s, with deterministic ids |
| `VectorSearchService` | Rungs 4–5: similarity search and `QuestionAnswerAdvisor` RAG |
| `HotelTools` | Rung 6: `@Tool` methods over `HotelService` / `RoomInventoryRepository` |
| `HotelAssistantService` | Rung 7: RAG + tools + `MessageWindowChatMemory`, returns a trace |

## Gemini specifics

These differ from the OpenAI defaults most tutorials assume:

- **Two keys, one value.** Chat reads `spring.ai.google.genai.api-key`; embeddings read `spring.ai.google.genai.embedding.api-key`. Both get `${GEMINI_API_KEY}`.
- **`text-embedding-004` is retired.** It now returns `404 ... not supported for embedContent`. The current model is `gemini-embedding-001`.
- **Ask for 768 dimensions explicitly.** `gemini-embedding-001` defaults to **3072**, and pgvector's HNSW index only supports up to **2000** — so the default silently cannot be indexed. `spring.ai.google.genai.embedding.text.dimensions` and `spring.ai.vectorstore.pgvector.dimensions` must agree, or every insert fails. It is the one setting to check before ingesting anything.
- **Scores cluster in a narrow band.** With this model a beach query scores matching hotels around 0.55 and unrelated ones around 0.50, so `similarity-threshold` does little filtering on its own and `top-k` does the real work. Do not read an absolute cosine score as a confidence level.
- **Task types are real.** Gemini embeddings take a `task-type`: `RETRIEVAL_DOCUMENT` when indexing, `RETRIEVAL_QUERY` when searching. The same sentence embeds differently depending on which you use.
- **Free tier.** AI Studio keys have a quota that comfortably covers this lab, so there is no billing setup.

### Swapping providers

Spring AI's point is that the provider is a dependency choice, not a code change — `ChatClient`, `EmbeddingModel`, `VectorStore`, and `@Tool` are all provider-neutral. To move to OpenAI, swap the starters in `voyage-app/pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

then set `spring.ai.openai.api-key` and change both `dimensions` settings to `1536` for `text-embedding-3-small`. No Java changes.

## Gotchas worth knowing

- **`ddl-auto: create-drop` empties the database on every restart.** Re-run **Seed catalog** and **Ingest** after each restart. That is why seeding is a visible lab step rather than a startup hook.
- **Re-ingesting must not duplicate.** Vector stores have no unique constraint. `HotelDocumentIngestor` derives a stable UUID per hotel and deletes before adding, so pressing Ingest twice replaces rather than doubling. Without that, every score is quietly skewed.
- **pgvector keys on a UUID column.** An id like `"hotel-1"` is rejected outright, which is why ids are name-based UUIDs.
- **Price belongs in metadata, never in the embedded text.** Numeric comparison is a filter or a tool call, not a similarity match.
- **Tool descriptions are API, not documentation.** They are the model's only guide to when a tool applies. A vague description is the most common reason a model picks the wrong tool or invents arguments.

## Tests

Everything runs with **no API key and no network**:

```bash
./mvnw test -pl voyage-app
```

`application-test.yml` sets `spring.ai.model.chat=none`, `spring.ai.model.embedding.text=none`, and excludes the pgvector and Google GenAI autoconfigurations. The embedding *connection* autoconfiguration is only `@ConditionalOnClass`, so the `none` property alone is not enough — it has to be excluded by name.

| Test | Covers |
|---|---|
| `CosineSimilarityTest` | The similarity formula, on vectors small enough to check by hand |
| `HotelDocumentIngestorTest` | Document shape, metadata, and stable ids |
| `HotelToolsTest` | Tools against H2, with no model involved |
| `HotelAssistantServiceTest` | Orchestration with a stub `ChatModel` and `VectorStore` |
| `AiPlaygroundSecurityTest` | 401 / 403 by role |
| `PgVectorIntegrationTest` | A real pgvector round trip with a deterministic fake embedding model |

`PgVectorIntegrationTest` prefers Testcontainers. If the Docker API is unreachable (some Docker Desktop socket setups), it falls back to a pgvector instance on port **5433**, which you can start with:

```bash
docker run -d --name voyage-pgvector-test -p 5433:5432 \
  -e POSTGRES_DB=voyage_vector_test -e POSTGRES_USER=voyage -e POSTGRES_PASSWORD=voyage \
  pgvector/pgvector:pg16
```

If neither is available the test skips, so the default build stays green without Docker. Port 5433 is deliberate — a test must never wipe the dev database on 5432.
