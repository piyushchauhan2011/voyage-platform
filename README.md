# Voyage Platform

A Maven monorepo for learning backend Java and Spring Boot — structured around the hotel/travel booking domain used across ecommerce, banking, and SaaS interviews.

## Modules

| Module | Stack | Purpose |
|---|---|---|
| [`voyage-app`](voyage-app/) | Spring Boot 4.1 · PostgreSQL · JPA | Spring fundamentals: IoC, DI, REST, JPA, bean lifecycle |
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
- **Phase 5** — add Kafka producer/consumer (infrastructure already running)
- **Phase 6** — add Redis caching layer (infrastructure already running)
- **Phase 7** — add `Dockerfile` per module, deploy to Kubernetes
