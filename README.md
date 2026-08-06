# Voyage Platform

A Maven monorepo for learning backend Java and Spring Boot — structured around the hotel/travel booking domain used across ecommerce, banking, and SaaS interviews.

## Modules

| Module | Stack | Purpose |
|---|---|---|
| [`voyage-app`](voyage-app/) | Spring Boot 4.1 · PostgreSQL · JPA · Kafka · Thymeleaf | Spring fundamentals: IoC, DI, REST, JPA, bean lifecycle, async messaging |
| [`java-mastery`](java-mastery/) | Plain Java 21 | Core Java: OOP, Collections internals, Streams & lambdas |

---

## Quick start

### 1. Start the infrastructure

```bash
docker compose up -d
```

| Service | URL / Port | Credentials |
|---|---|---|
| PostgreSQL | `localhost:5432` | `voyage / voyage` |
| pgAdmin | http://localhost:5050 | `admin@voyage.com / admin` |
| Redis | `localhost:6379` | — |
| Kafka | `localhost:9092` | — |

### 2. Run the Spring Boot app

```bash
./mvnw spring-boot:run -pl voyage-app
```

The app connects to the Dockerised Postgres and creates the schema automatically (`ddl-auto=create-drop`).

Try the API:

```bash
# List all hotels
curl http://localhost:8080/api/hotels

# Create a hotel
curl -X POST http://localhost:8080/api/hotels \
  -H "Content-Type: application/json" \
  -d '{"name":"Grand Hyatt","city":"Tokyo","pricePerNight":220}'

# Filter by city
curl "http://localhost:8080/api/hotels/search?city=Tokyo"

# Spring Actuator health
curl http://localhost:8080/actuator/health

# Kafka dashboard
open http://localhost:8080/ui/kafka
```

### 3. Run the Java mastery demos

```bash
./mvnw exec:java -pl java-mastery -Dexec.mainClass=com.voyage.mastery.Main
```

### 4. Run tests (no Docker needed)

```bash
./mvnw test -pl voyage-app      # uses H2 in-memory DB
./mvnw test                     # all modules
```

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

### Spring Data JPA
`Hotel.java` — entity lifecycle: `NEW → MANAGED → DETACHED → REMOVED`.

`HotelRepository.java` — Spring Data derives the SQL for `findByCity` from the method name at startup. No implementation needed.

Key interview topics to explore next:
- N+1 problem (fetch a hotel's rooms — watch the SQL with `show-sql: true`)
- `@Transactional` propagation and isolation levels
- `EXPLAIN ANALYZE` on your queries in pgAdmin

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

---

## Infrastructure reference

### Kafka dashboard
- Open `http://localhost:8080/ui/kafka`
- Use `/api/auth/login` credentials or paste an admin bearer token into the page
- Trigger hotel create, update, or delete operations from the dashboard
- Watch the event feed show the topic, partition, offset, message key, and processed timestamp
- Open `http://localhost:8080/ui/kafka/history` to inspect the full processed-event history and any dead-lettered records

### Connecting pgAdmin to Postgres
1. Open http://localhost:5050
2. Add server → Host: `postgres`, Port: `5432`, DB: `voyage_db`, User: `voyage`, Password: `voyage`
3. Run `EXPLAIN ANALYZE` on your queries to study query plans

### Stopping everything
```bash
docker compose down          # stop containers, keep volumes
docker compose down -v       # stop containers AND wipe volumes (fresh DB)
```

---

## Roadmap coverage

This repo is structured to grow with the learning plan:

- **Phase 1 (now)** — `java-mastery`: OOP, Collections, Streams
- **Phase 2–3 (now)** — `voyage-app`: Spring Core, REST, JPA
- **Phase 4** — add Spring Security + JWT to `voyage-app`
- **Phase 5 (complete)** — Kafka producer/consumer + Thymeleaf event dashboard for hotel mutations
- **Phase 6** — add Redis caching layer (infrastructure already running)
- **Phase 7** — add `Dockerfile` per module, deploy to Kubernetes
