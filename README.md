# Voyage Platform

A Maven monorepo for learning backend Java and Spring Boot — structured around the hotel/travel booking domain used across ecommerce, banking, and SaaS interviews.

## Modules

| Module | Stack | Purpose |
|---|---|---|
| [`voyage-app`](voyage-app/) | Spring Boot 4.1 · PostgreSQL · JPA · Kafka · RabbitMQ · Elasticsearch · Thymeleaf · Spring AI | Spring fundamentals: IoC, DI, REST, JPA, bean lifecycle, async messaging, search, AI |
| [`java-mastery`](java-mastery/) | Plain Java 21 | Core Java: OOP, Collections, Streams, JVM internals, Multithreading |

---

## Quick start

### 1. Start the infrastructure

All Compose services are **opt-in via profiles** (nothing starts on a bare `docker compose up`). For day-to-day `voyage-app` work:

```bash
docker compose --profile app up -d
```

| Profile | What it starts | Use when |
|---|---|---|
| `app` | Postgres, pgAdmin, Redis, Kafka, Zookeeper, RabbitMQ, Elasticsearch | Running the Spring app |
| `postgres` | Postgres + pgAdmin | Postgres / JPA / SQL labs |
| `redis` | Redis | Cache / eviction labs |
| `kafka` | Zookeeper + Kafka | Kafka learning |
| `rabbitmq` | RabbitMQ | RabbitMQ lab |
| `elasticsearch` | Elasticsearch | Hotel full-text search lab |
| `observability` | Postgres, Redis, Kafka + exporters, Prometheus, Grafana | SRE / Grafana lab |
| `jenkins` | Jenkins | Local CI lab (§4c) |

Combine freely, e.g. `docker compose --profile kafka --profile redis up -d`. Naming a service also enables its profile: `docker compose up -d postgres`.

| Service | URL / Port | Credentials |
|---|---|---|
| PostgreSQL (pgvector) | `localhost:5432` | `voyage / voyage` |
| pgAdmin | http://localhost:5050 | `admin@voyage.com / admin` |
| Redis | `localhost:6379` | `maxmemory 64mb`, `allkeys-lru` (eviction lab) |
| Kafka | `localhost:9092` | — |
| RabbitMQ | `localhost:5672` (AMQP) · http://localhost:15672 (UI) | `guest / guest` |
| Elasticsearch | http://localhost:9200 | security disabled (local lab) |
| Prometheus | http://localhost:9090 | scrapes app + exporters (`--profile observability`) |
| Grafana | http://localhost:3000 | `admin / admin` — dashboard **Voyage SRE Lab** |
| Jenkins | http://localhost:8081 | unlock with initial admin password (see §4c) |

### 2. Run the Spring Boot app

```bash
./mvnw spring-boot:run -pl voyage-app
```

The app connects to the Dockerised Postgres and creates the schema automatically (`ddl-auto=create-drop`).

Try the API:

```bash
# List all hotels
curl http://localhost:8080/api/v1/hotels

# Create a hotel
curl -X POST http://localhost:8080/api/v1/hotels \
  -H "Content-Type: application/json" \
  -d '{"name":"Grand Hyatt","city":"Tokyo","pricePerNight":220}'

# Filter by city
curl "http://localhost:8080/api/v1/hotels?city=Tokyo"

# Spring Actuator health
curl http://localhost:8080/actuator/health

# Prometheus scrape (Micrometer)
curl http://localhost:8080/actuator/prometheus | head

# Kafka dashboard
open http://localhost:8080/ui/kafka

# Elasticsearch hotel search (Thai/English)
open http://localhost:8080/ui/search

# Grafana SRE lab (CPU / memory / latency / deadlock / Kafka lag / Redis eviction)
open http://localhost:3000
# see observability/README.md for chaos endpoints + runbooks

# Spring AI hotel assistant (needs GEMINI_API_KEY — see Phase 8)
open http://localhost:8080/ui/ai
```

### 3. Run the Java mastery demos

```bash
./mvnw exec:java -pl java-mastery -Dexec.mainClass=com.voyage.mastery.Main
```

### 4. Run tests

```bash
# Unit / in-process tests (Surefire) — H2, EmbeddedKafka; no Docker required
./mvnw test -pl voyage-app
./mvnw test

# Integration tests (Failsafe) — *IntegrationTest / *IT; Testcontainers when Docker is available
./mvnw verify -pl voyage-app -DskipUnitTests=true

# Full verify: unit + integration + quality plugins bound to verify
./mvnw verify -pl voyage-app
```

### 4b. Formatting, lint, static analysis, coverage

```bash
./mvnw spotless:apply          # format sources (google-java-format)
./mvnw spotless:check          # fail if formatting is dirty
./mvnw checkstyle:check        # style lint
./mvnw pmd:check pmd:cpd-check # static analysis + complexity + duplication
./mvnw -DskipTests compile spotbugs:check
./mvnw test jacoco:report jacoco:check -pl voyage-app   # coverage (≥40% lines)
```

GitHub Actions (`.github/workflows/ci.yml`) runs these on every push/PR to `main`: parallel **quality**, **unit-tests** (+ JaCoCo), and **integration-tests** (Docker/Testcontainers), then **package**.

### 4c. Local Jenkins (CI learning lab)

Jenkins is **not** started by default. Use it to practice Pipeline jobs locally; GitHub Actions stays the source of truth for PRs.

```bash
# Start only Jenkins (other services can already be up)
docker compose --profile jenkins up -d jenkins

# Unlock password (first boot)
docker exec voyage-jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# Stop when done
docker compose --profile jenkins stop jenkins
```

1. Open http://localhost:8081 and paste the unlock password.
2. Install **suggested plugins** (includes Pipeline).
3. Create an admin user, then **New Item → Pipeline**.
4. Under **Pipeline** → Definition: *Pipeline script from SCM* → Git → your clone URL (GitHub remote, or a local `file:///path/to/voyage-platform` if the agent can read it) → Script Path `Jenkinsfile`.
5. **Build Now**. The first run is slow (Maven deps + Testcontainers images); later builds reuse the Docker/Maven cache.

The root `Jenkinsfile` mirrors the GitHub Actions stages: parallel Quality / Unit tests / Integration tests, then Package.

### 5. Build everything

```bash
./mvnw clean install -DskipTests
```

---

## voyage-app — what to study

### Spring IoC & DI
`AppConfig.java` — `@Configuration` + `@Bean`: explicit bean registration for objects you construct yourself (e.g. third-party clients).

`HotelService.java` — constructor injection. Ask yourself: *why is this better than `@Autowired` on a field?*

> Answer: field is immutable (`final`), the dependency is visible at construction time, and you can unit-test by passing a mock — no Spring context required.

### Bean lifecycle
`BeanLifecycleDemo.java` — start the app and watch the logs:

```
[Lifecycle 1] BeanLifecycleDemo instantiated
[Lifecycle 2] @PostConstruct — all dependencies are injected
[Lifecycle 3] @PreDestroy — shutting down          ← hit Ctrl+C to see this
```

The full lifecycle:
```
Instantiation → Dependency injection → @PostConstruct → [ready] → @PreDestroy
```

### REST API
`HotelController.java` — covers the four annotations you'll use in every controller: `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`.

`HotelNotFoundException.java` — `@ResponseStatus(NOT_FOUND)`: the simplest way to map an exception to an HTTP status without a global `@ControllerAdvice`.

### Phase 4 — Security, ABAC, plans, payments

JWT auth is already in place (`/api/v1/auth/**`). Phase 4 completion adds attribute-based access on top of roles:

| Role | Typical access |
|---|---|
| `CUSTOMER` | Book hotels, view/cancel own bookings, view own payments |
| `HOTEL_MANAGER` | Manage **owned** hotels; inventory/refunds gated by hotel SaaS plan |
| `ADMIN` | Platform-wide; assign managers; set SaaS plans; labs |

**Hotel SaaS plans** (`FREE` / `PRO` / `ENTERPRISE`) live on `Hotel.saasPlan` — gate max hotels per manager, inventory writes, and manager refunds. Admin upgrades via `PATCH /api/v1/hotels/{id}/management`.

**Booking rate plans** (`FLEXIBLE` / `NON_REFUNDABLE`) live on `Booking.ratePlan` — NON_REFUNDABLE is 15% off and blocks customer refunds. FLEXIBLE auto-refunds on cancel.

**Mock payments** — charge stays inside the booking TX (`paymentToken=decline` fails). REST:

```text
GET  /api/v1/payments/{id}
GET  /api/v1/payments?bookingId=
POST /api/v1/payments/{id}/refund
```

Manual demo: `bash api_manual_checks/run_abac_payment_flow.sh`

### Spring Data JPA
`Hotel.java` — entity lifecycle: `NEW → MANAGED → DETACHED → REMOVED`.

`HotelRepository.java` — Spring Data derives the SQL for `findByCity` from the method name at startup. No implementation needed.

### JPA skills lab
Hands-on entity lifecycle, lazy/eager loading, N+1, JPQL, Criteria API, Specifications, and Spring `@Transactional` — taught against the real booking flow (reserve → charge → commit).

| Path | What it is |
|---|---|
| [`jpa-lab/`](jpa-lab/) | Curated learning path mapped to Voyage source |
| `/ui/jpa` | Thymeleaf workbench (admin JWT) |
| `/api/jpa/playground` | Same labs via REST (ADMIN) |

Suggested learning path:

1. Start Postgres + the app, open http://localhost:8080/ui/jpa, login as ADMIN.
2. **Seed lab fixtures**, then Lifecycle → persist / detach & mutate.
3. Loading → **N+1 demo** vs **EntityGraph fix** (compare `queryCount`).
4. Query APIs → JPQL / Criteria / Specifications.
5. Transactions → booking success, rollback (`decline`), propagation map, lock contention.
6. For SQL-level isolation / `FOR UPDATE`, continue in the **PostgreSQL lab** below.

### PostgreSQL lab
Hands-on indexes, composite indexes, the query planner, `EXPLAIN ANALYZE`, partitioning, locks, and isolation.

| Path | What it is |
|---|---|
| [`postgres-lab/`](postgres-lab/) | Curated `.sql` scripts for `psql` / pgAdmin |
| `/ui/postgres` | Thymeleaf workbench (admin JWT) |
| `/api/postgres/playground` | Same labs via REST (ADMIN) |

Suggested learning path:

1. Start Postgres + the app, open http://localhost:8080/ui/postgres, login as ADMIN.
2. Click **Seed volume data** (creates Lab Hotel 1–50 and bulk bookings).
3. Run **booking_by_hotel** explain → drop `idx_booking_hotel` → explain again → recreate index.
4. Compare composite vs single-column with **booking_by_hotel_checkin**.
5. **Setup partitions** on `pg_lab_bookings`, then **Explain pruning**.
6. Run READ COMMITTED vs REPEATABLE READ demos; compare with `BookingService` (`REPEATABLE_READ`) and `RoomInventoryRepository.findForUpdate` (`FOR UPDATE`). Pair with the **JPA skills lab** for Spring annotation semantics on the same booking story.

Or from SQL:

```bash
docker cp postgres-lab/. voyage-postgres:/tmp/postgres-lab/
docker exec -it voyage-postgres psql -U voyage -d voyage_db -f /tmp/postgres-lab/00_seed_volume.sql
docker exec -it voyage-postgres psql -U voyage -d voyage_db -f /tmp/postgres-lab/01_indexes_explain.sql
```

Quick API examples (admin bearer token):

```bash
# Seed volume data
curl -X POST http://localhost:8080/api/postgres/playground/seed \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# EXPLAIN ANALYZE — bookings by hotel (your hotel_id example)
curl -X POST http://localhost:8080/api/postgres/playground/explain \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"scenario":"booking_by_hotel"}'

# Drop / recreate the hotel_id index and re-run explain
curl -X POST http://localhost:8080/api/postgres/playground/indexes/idx_booking_hotel/drop \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
curl -X POST http://localhost:8080/api/postgres/playground/indexes/idx_booking_hotel/create \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Partitioning
curl -X POST http://localhost:8080/api/postgres/playground/partitioning/setup \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
curl http://localhost:8080/api/postgres/playground/partitioning/explain \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Isolation demo
curl -X POST http://localhost:8080/api/postgres/playground/isolation/demo \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"scenario":"repeatable_read_stable"}'
```

Helper script: `bash api_manual_checks/seed_postgres_lab.sh`

### RabbitMQ lab
Hands-on AMQP for **Exchange**, **Queue**, **Routing Key**, and **Consumer** — taught as voyage **task queues** (background jobs), contrasted with Kafka event streaming.

| Path | What it is |
|---|---|
| [`rabbitmq-lab/`](rabbitmq-lab/) | Shell recipes + Kafka comparison notes |
| `/ui/rabbitmq` | Thymeleaf workbench (admin JWT) |
| `/api/rabbitmq/playground` | Same labs via REST (ADMIN) |
| http://localhost:15672 | RabbitMQ management UI (`guest` / `guest`) |

Suggested learning path:

1. `docker compose --profile app up -d` (or `--profile rabbitmq` alone), start the app, open http://localhost:8080/ui/rabbitmq, login as ADMIN.
2. **Setup topology** — direct exchange `voyage.jobs` bound to booking/email queues.
3. Publish `booking.confirm` or `email.send`, then **Refresh consumed** to see the consumer delivery.
4. Run **Routing demo** (matched key vs unbound `unknown.job`).
5. **Load comparison**, then open [`/ui/kafka`](http://localhost:8080/ui/kafka) for the hotel event stream.

Or from the management API scripts:

```bash
bash rabbitmq-lab/00_topology.sh
bash rabbitmq-lab/01_publish_routing.sh
bash rabbitmq-lab/02_consume_ack.sh
```

Quick API examples (admin bearer token):

```bash
# Declare / refresh topology
curl -X POST http://localhost:8080/api/rabbitmq/playground/setup \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Publish a job
curl -X POST http://localhost:8080/api/rabbitmq/playground/publish \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"routingKey":"booking.confirm","payload":"confirm-42"}'

# See what LabJobConsumer recorded
curl http://localhost:8080/api/rabbitmq/playground/consumed \
  -H "Authorization: Bearer <ADMIN_TOKEN>"

# Kafka vs RabbitMQ comparison payload
curl http://localhost:8080/api/rabbitmq/playground/compare \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

Helper script: `bash api_manual_checks/seed_rabbitmq_lab.sh`

### Kafka & async messaging
Phase 5 is implemented as a hotel event stream:

```text
HotelService
  |
  | publish after create / update / delete
  v
hotel-events topic
  |
  v
HotelEventListener (consumer group: voyage-hotel-events-ui)
  |
  v
processed_hotel_events table + /ui/kafka dashboard
```

Where to study it:

- `HotelService.java` — the mutation boundary where hotel create, update, and delete publish domain events after persistence succeeds.
- `com.voyage.app.kafka.HotelEvent` — the event envelope: event id, schema version, event type, hotel data, and timestamp.
- `HotelEventPublisher.java` — producer logic that serialises the event and sends it to Kafka using the hotel id as the message key.
- `HotelEventListener.java` — consumer logic that reads the topic and records topic name, partition, offset, and processing time.
- `ProcessedHotelEvent.java` — idempotent event storage showing how at-least-once delivery can still be made safe for consumers.
- `DeadLetterHotelEvent.java` — records messages that exhausted retries and were rerouted to the dead-letter topic.
- `/ui/kafka` — a small Thymeleaf dashboard that lets you trigger admin hotel writes and watch consumed events appear.
- `/ui/kafka/history` — a clearer history page that separates successfully processed events from dead letters.

Map the core Kafka concepts to this implementation:

- `Producer`: `HotelEventPublisher` sends messages whenever hotel data changes.
- `Topic`: `hotel-events` is the append-only log.
- `Partition`: the hotel id is used as the Kafka message key, so events for one hotel stay ordered on the same partition.
- `Offset`: every consumed record stores the Kafka offset in `processed_hotel_events` and the dashboard shows it.
- `Consumer group`: the listener runs under one explicit group id, so multiple consumers could share work later.
- `Replication`: the local compose setup is single-broker, so replication is a concept to learn here rather than a behavior you can fully observe locally.
- `Retention`: Kafka keeps the event log for a configured period even after the app has processed it.
- `At least once`: this app is designed around at-least-once delivery; duplicate deliveries are handled with unique event ids in the consumer store.
- `Retries`: the listener retries failed records before giving up.
- `Dead-letter topic`: exhausted failures are published to `hotel-events.DLT` and persisted for inspection.
- `Exactly once`: not implemented; that would require transactional semantics and tighter producer-consumer coordination.

Interview follow-ups for this phase:
- what changes if you split one topic into separate `hotel-created`, `hotel-updated`, and `hotel-deleted` topics?
- what breaks if the consumer crashes after processing the DB write but before committing the offset?
- when should you use a dead-letter topic instead of retrying inline?
- how would you evolve `HotelEvent` to schema version 2 without breaking old consumers?

### Redis caching
Phase 6 now starts from the simplest cache story in this repo:

```text
Hotel availability / hotel details read
        |
        v
      Redis
        |
        v
     Postgres
```

What is implemented now:

- `HotelService.findById(...)` caches single-hotel reads in Redis.
- `HotelService.findByCity(...)` caches city-filtered reads in Redis.
- `save`, `update`, and `delete` evict or refresh affected cache entries after the database write succeeds.
- The test profile keeps Redis disabled and falls back to an in-memory cache so ordinary tests stay fast.

Redis concepts to study in this phase:

- `String` — single values, counters, flags, one-key cache entries.
- `Hash` — field-based objects such as a hotel snapshot.
- `List` — recent searches or append-only activity feeds.
- `Set` — unique watchers, unique hotel ids, unique user ids.
- `Sorted Set` — rankings such as cheapest hotels, popularity, or scoreboards.
- `TTL` — short-lived availability or search caches.
- `Eviction` — what Redis removes when memory fills up, and how policy choice changes behavior.
- `Pub/Sub` — fire-and-forget fan-out for live notifications inside one running system.
- `Distributed locks` — short reservation windows or protecting one critical section across nodes.

The app also exposes a Redis playground under `/api/redis/playground` so you can exercise the data structures directly.
All Redis playground endpoints require an admin bearer token.

Quick examples:

```bash
# String with TTL
curl -X POST http://localhost:8080/api/redis/playground/strings/demo:hotel:1 \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"value":"available","ttlSeconds":120}'

# Hash
curl -X POST http://localhost:8080/api/redis/playground/hashes/demo:hotel:1 \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"fields":{"name":"Grand Hyatt","city":"Tokyo","pricePerNight":"320"},"ttlSeconds":300}'

# List
curl -X POST http://localhost:8080/api/redis/playground/lists/demo:recent-searches \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"values":["Tokyo","Paris","Dubai"],"ttlSeconds":180}'

# Set
curl -X POST http://localhost:8080/api/redis/playground/sets/demo:watchers \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"values":["alice","bob","alice"],"ttlSeconds":180}'

# Sorted set
curl -X POST http://localhost:8080/api/redis/playground/sorted-sets/demo:popularity \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"values":[{"value":"tokyo","score":98},{"value":"paris","score":87}],"ttlSeconds":300}'

# Pub/Sub publish
curl -X POST http://localhost:8080/api/redis/playground/pubsub/publish \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"message":"booking-created"}'

# Acquire a lock for 30 seconds
curl -X POST http://localhost:8080/api/redis/playground/locks/inventory:hotel-42/acquire \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"owner":"booking-service-instance-1","ttlSeconds":30}'
```

Suggested learning path:

1. Call one hotel read twice and confirm the second call is faster or no longer hits Postgres.
2. Update or delete that hotel and confirm the cache gets refreshed or evicted.
3. Create keys in the playground and inspect them with `redis-cli`.
4. Watch TTL countdown with `GET /api/redis/playground/ttl/{key}`.
5. Publish a message, then inspect `GET /api/redis/playground/pubsub/messages`.
6. Acquire the same lock twice with different owners and confirm only one wins.

How Redis maps to the services you want to learn next:

- `User Service` — session state, refresh-token denylists, rate limits.
- `Hotel Service` — hotel detail cache, city-search cache.
- `Inventory Service` — short-lived availability cache, reservation locks.
- `Booking Service` — idempotency keys, temporary booking holds.
- `Payment Service` — deduplication keys, workflow checkpoints.
- `Notification Service` — transient fan-out with Pub/Sub.

### Profiles
`application.yml` + `application-dev.yml` — base config with profile overlays.  
`application-test.yml` (in `src/test/resources`) — swaps Postgres for H2 during tests.

Activate a profile: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl voyage-app`

---

## java-mastery — what to study

### OOP — `oop/`
`PaymentProvider` (interface) → `StripePayment` / `PaypalPayment` (implementations).

This is the *Dependency Inversion Principle*: `PaymentService` depends on the interface, not the concrete class. You can swap providers without touching the caller — Spring's entire IoC container is built on this idea.

`PaymentResult` is a **record** (Java 16+): the compiler generates constructor, getters, `equals`, `hashCode`, and `toString` — ~40 lines of boilerplate gone.

### Collections — `collections/`
`CollectionsDemo.java` covers the interview essentials:

| Structure | Backed by | Access | Insert (middle) |
|---|---|---|---|
| `ArrayList` | Array | O(1) | O(n) shift |
| `LinkedList` | Doubly-linked list | O(n) | O(1) at ends |
| `HashMap` | Array of buckets | O(1) avg | O(1) avg |
| `PriorityQueue` | Binary heap | O(1) peek | O(log n) |

HashMap internals flow: `key.hashCode()` → bucket index → `equals()` resolves collisions → entry.

`ConcurrentHashMap` vs `HashMap`: thread-safe, segments the map to reduce lock contention, no null keys/values.

### Streams & Lambdas — `streams/`
`StreamsDemo.java` covers the core pipeline:

```java
bookings.stream()
  .filter(Booking::confirmed)     // intermediate — lazy
  .map(Booking::hotelName)        // intermediate — lazy
  .collect(Collectors.toList());  // terminal — triggers execution
```

Also covers `groupingBy`, `flatMap`, `mapToDouble().sum()`, and `Optional` from `average()`.

### JVM internals — `jvm/`
`JvmDemo.java` walks the senior interview map:

```
.class bytecode  →  JVM  →  Heap / Stack / Metaspace  →  GC
```

- **Heap** — objects & arrays (shared); sized with `-Xms` / `-Xmx`
- **Stack** — per-thread frames (locals, calls); sized with `-Xss`
- **Metaspace** — class metadata (native memory); capped with `-XX:MaxMetaspaceSize`

Heap generations: Eden → Survivor → Old. Most objects die in Eden (minor GC); long-lived work promotes to Old.

| Collector | Goal | Typical use |
|---|---|---|
| Parallel | Throughput | Batch / offline jobs |
| G1 | Balanced pauses (default) | Most web services |
| ZGC | Ultra-low latency | Large heaps, tight p99 |

Flags cheat sheet is printed by the demo (`-XX:+UseG1GC`, `-XX:+UseZGC`, `-Xlog:gc*`, …).

### Multithreading — `concurrency/`
`ConcurrencyDemo.java` covers the must-master APIs:

`Thread` / `Runnable` → `Callable` + `Future` → `CompletableFuture` → `ExecutorService` vs `ForkJoinPool` → `synchronized` / `ReentrantLock` → `volatile` (visibility ≠ atomicity) → `AtomicInteger` vs plain `int++`.

`DoubleBookingDemo.java` is the interview centerpiece: **1000 threads, 1 room**.

1. **Broken** — unsynchronized check-then-act → oversell
2. **Fixed A** — `synchronized`
3. **Fixed B** — `ReentrantLock`
4. **Fixed C** — `AtomicInteger.compareAndSet`

Production sequel in `voyage-app`: `RoomInventoryRepository.findForUpdate` (`PESSIMISTIC_WRITE`) + `InventoryService.reserveRoom` under `@Transactional` — same race, row-level DB lock.

---

## Infrastructure reference

### Kafka dashboard
- Open `http://localhost:8080/ui/kafka`
- Use `/api/auth/login` credentials or paste an admin bearer token into the page
- Trigger hotel create, update, or delete operations from the dashboard
- Watch the event feed show the topic, partition, offset, message key, and processed timestamp
- Open `http://localhost:8080/ui/kafka/history` to inspect the full processed-event history and any dead-lettered records
- Use the history page to retry dead-letter records with an admin bearer token and an optional corrected payload override

### QA manual checks
- End-to-end auth + hotel + Kafka happy path: `bash api_manual_checks/run_auth_hotel_flow.sh`
- Kafka retries + dead-letter + replay path: `bash api_manual_checks/run_kafka_dlt_flow.sh`
- Redis cache + playground verification path: `bash api_manual_checks/run_redis_flow.sh`
- QA user lifecycle helper: `bash api_manual_checks/manage_qa_user.sh --help`

Manual QA steps for the DLT and replay flow:
1. Start Docker and the Spring Boot app.
2. Open `http://localhost:8080/ui/kafka/history`.
3. Create an admin-capable QA user with `bash api_manual_checks/manage_qa_user.sh create --username qa_admin_manual --promote`.
4. Log in as that user through `/api/auth/login` or the dashboard and keep the bearer token.
5. Publish a malformed payload into `hotel-events`, for example `BROKEN_EVENT_MANUAL`, through `POST /api/kafka/publish-raw` as documented in `api_manual_checks/README.md`.
6. Confirm the record appears in `/ui/kafka/dead-letters` or on the history page.
7. Paste the admin bearer token into the history page, replace the dead-letter payload with valid `HotelEvent` JSON, and click `Retry from UI`.
8. Confirm the replayed event appears in the processed-event history and the dead-letter row becomes `RESOLVED`.

### Connecting pgAdmin to Postgres
1. Open http://localhost:5050
2. Add server → Host: `postgres`, Port: `5432`, DB: `voyage_db`, User: `voyage`, Password: `voyage`
3. Run the scripts in [`postgres-lab/`](postgres-lab/) — start with `00_seed_volume.sql` then `01_indexes_explain.sql` — or use `/ui/postgres`

### Stopping everything
```bash
docker compose down          # stop containers, keep volumes
docker compose down -v       # stop containers AND wipe volumes (fresh DB)
```

### Phase 7 — Docker & Kubernetes

All infra services and `voyage-app` run inside a local [kind](https://kind.sigs.k8s.io) cluster.

#### Prerequisites — install kind

```bash
brew install kind
```

#### Create the cluster

```bash
kind create cluster
# Sets kubectl context to "kind-kind" automatically
kubectl cluster-info --context kind-kind
```

#### Build the image

```bash
# Run from the repo root (build context is the whole monorepo)
docker build -t voyage-app:latest -f voyage-app/Dockerfile .
```

The Dockerfile is a two-stage build:
- **builder** (`eclipse-temurin:21-jdk-alpine`) — runs `mvnw package -pl voyage-app -DskipTests` and produces the fat jar
- **runtime** (`eclipse-temurin:21-jre-alpine`) — copies the jar and runs as a non-root `voyage` user

Dependency layers are cached separately from source, so a source-only change skips the `dependency:go-offline` step.

#### Load & deploy

```bash
# Load the image into kind nodes (kind clusters are isolated from the Docker daemon)
kind load docker-image voyage-app:latest

# Apply all manifests — namespace is created first (00-prefix), then everything else
kubectl apply -f k8s/

# Watch all pods come up (ctrl-c when all show 1/1 Running — takes ~90 s)
kubectl get pods -n voyage -w
```

#### Access the app on macOS

On macOS, kind runs inside Docker so NodePort `30080` is not reachable from `localhost` directly. Use port-forward instead:

```bash
kubectl port-forward -n voyage svc/voyage-app 8080:8080
```

Then in another terminal:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/hotels          # expects JWT — returns 401 without a token
open http://localhost:8080/ui/kafka
```

Keep the port-forward running while you use the app. Press `ctrl-c` to stop it.

#### Kubernetes manifest map

| File | What it creates |
|---|---|
| `k8s/00-namespace.yaml` | `voyage` namespace (prefixed so it applies before other resources) |
| `k8s/voyage-app-config.yaml` | ConfigMap (env vars) + Secret (DB password, JWT key) |
| `k8s/postgres.yaml` | Deployment + ClusterIP Service + 1 Gi PVC |
| `k8s/redis.yaml` | Deployment + ClusterIP Service |
| `k8s/zookeeper.yaml` | Deployment + ClusterIP Service |
| `k8s/kafka.yaml` | Deployment + ClusterIP Service |
| `k8s/voyage-app.yaml` | Deployment (with init containers) + NodePort Service (30080) |

Key differences from docker-compose:
- `KAFKA_ADVERTISED_LISTENERS` uses in-cluster DNS (`kafka:9092`) instead of `localhost:9092`
- `voyage-app` has init containers that wait for Postgres and Kafka to be reachable before the Spring Boot process starts
- `enableServiceLinks: false` on the kafka pod — Kubernetes would otherwise inject `KAFKA_PORT` from the Service, which Confluent Platform misinterprets as a broker config property and crashes
- `SPRING_JPA_HIBERNATE_DDL_AUTO: create` overrides the local `create-drop` — in k8s a rolling update runs the old pod's shutdown (which drops all tables) after the new pod has already started, so `create` is used instead: it recreates tables fresh on every startup but never drops them on shutdown
- `strategy: type: Recreate` on the voyage-app Deployment — ensures the old pod is fully terminated before the new pod starts, so both cannot hold the database schema simultaneously

#### Teardown

```bash
kubectl delete -f k8s/          # remove all Kubernetes resources and the PVC
kind delete cluster             # delete the kind cluster itself
```

---

### Phase 8 — Spring AI hotel assistant

A hotel assistant that answers **"Find hotels near beach under $100"**, built one concept at a time: chat, embeddings, vector search, RAG, tool calling, agents. Uses Spring AI 2.0 with Google Gemini and pgvector.

The question is two questions in one coat, and the split is the lesson:

```text
"Find hotels near beach under $100"
        |
        +-- "near beach"  --> fuzzy    --> embedding similarity over descriptions (pgvector)
        |
        +-- "under $100"  --> exact    --> tool call running SQL against the hotels table
        |
        v
   answer + trace: retrieved docs, tool calls, tokens
```

Similarity search cannot compare numbers; a `WHERE` clause cannot tell that "sand and surf" means beach. The agent decides which to use and combines them.

#### Setup

Get a free key from [Google AI Studio](https://aistudio.google.com/apikey), add it to `.env` as `GEMINI_API_KEY=...`, then:

```bash
set -a && . ./.env && set +a     # Spring Boot does not read .env by itself
docker compose --profile app up -d
./mvnw spring-boot:run -pl voyage-app
open http://localhost:8080/ui/ai
```

**Docker Compose reads `.env` automatically; Spring Boot does not.** `./mvnw spring-boot:run` only sees what is exported in your shell, so putting the key in `.env` is not sufficient on its own. Every other variable in `application.yml` has a default matching `.env`, which is why this only surfaces with `GEMINI_API_KEY`. Check with `/api/ai/playground/status` — it reports `apiKeyConfigured`.

The app still boots without a key — the other phases keep working and the AI endpoints return a clear 503 explaining what is missing.

> Postgres now runs `pgvector/pgvector:pg16` instead of `postgres:16-alpine`, since the vector store needs the `vector` extension. Same PG16 base, so the existing `postgres-data` volume carries over.

#### The ladder

Each rung is a panel at `/ui/ai` and an endpoint under `/api/ai/playground` (ADMIN JWT).

| # | Rung | Endpoint | What to notice |
|---|---|---|---|
| 0 | Seed catalog | `POST /seed-catalog` | ~20 hotels whose descriptions never say "beach" literally |
| 1 | Chat | `POST /chat` | Ask for a Voyage hotel — it invents one. The baseline failure |
| 2 | Embedding | `POST /embed` | Related phrases score high with no words in common |
| 3 | Ingest | `POST /ingest` | Hotels become text + 768-float vectors + metadata |
| 4 | Search | `POST /search` | Retrieval with scores, before any prose hides the mechanics |
| 5 | RAG | `POST /rag` | Retrieved rows go into the prompt, so answers cite real hotels |
| 6 | Tools | `GET /tools` | The JSON contract the model programs against |
| 7 | Assistant | `POST /assistant` | RAG + tools + chat memory, with a full trace |

Two moments are worth waiting for. At rung 4, searching "somewhere near the sand and surf" returns hotels a SQL `LIKE` would miss entirely. At rung 5, asking for hotels "under $100" without a filter often returns a $320 resort — which is exactly why rung 6 exists.

Helper script: `bash api_manual_checks/seed_ai_lab.sh` — walks the whole ladder from the terminal and prints the trace at each rung.

Full walkthrough, curl examples, and Gemini-specific gotchas: [`ai-lab/README.md`](ai-lab/README.md).

---

### Phase 9 — Elasticsearch hotel search + Thai i18n

Full-text hotel search with Spring Data Elasticsearch. **Postgres stays the source of truth**; Elasticsearch is a derived index with English + Thai analyzers. The UI at `/ui/search` uses Thymeleaf, HTMX result fragments, and `messages` / `messages_th` locale switching.

```bash
docker compose --profile elasticsearch up -d   # or --profile app
./mvnw spring-boot:run -pl voyage-app
open http://localhost:8080/ui/search
# Toggle ไทย, Seed + Reindex as ADMIN, then search beach vs ชายหาด
```

Contrast with:

- JPA filters: `GET /api/v1/hotels?city=Bangkok`
- Semantic search: [`/ui/ai`](http://localhost:8080/ui/ai) (pgvector)

Lab notes and sample curls: [`search-lab/README.md`](search-lab/README.md). Helper: `bash api_manual_checks/seed_search_lab.sh`.

---

## Roadmap coverage

This repo is structured to grow with the learning plan:

- **Phase 1 (now)** — `java-mastery`: OOP, Collections, Streams, JVM, Multithreading
- **Phase 2–3 (now)** — `voyage-app`: Spring Core, REST, JPA
- **Phase 4 (complete)** — Spring Security + JWT, ABAC roles (`CUSTOMER` / `HOTEL_MANAGER` / `ADMIN`), hotel SaaS plans, booking rate plans, mock payments
- **Phase 5 (complete)** — Kafka producer/consumer + Thymeleaf event dashboard for hotel mutations
- **Phase 6 (in progress)** — Redis-backed hotel caching + Redis playground for Strings, Hashes, Lists, Sets, Sorted Sets, TTL, Pub/Sub, and locks
- **Phase 7 (complete)** — `voyage-app/Dockerfile` (multi-stage) + full Kubernetes deployment in `k8s/`
- **Phase 8 (complete)** — Spring AI hotel assistant: Gemini chat, embeddings, pgvector, RAG, tool calling, and an agent at `/ui/ai`
- **Phase 9 (complete)** — Elasticsearch hotel search with Thai/English analyzers, HTMX UI, and i18n at `/ui/search`
