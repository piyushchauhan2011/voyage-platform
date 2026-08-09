# JPA Skills Lab (Booking Deep Dive)

Hands-on Hibernate / Spring Data JPA using Voyage's real `Booking` flow — not a toy schema.

Teaching vehicle:

```
BEGIN (REPEATABLE_READ)
  check availability / reserve nights (SELECT … FOR UPDATE)
  persist Booking PENDING
  charge Payment (joins same TX)
  CONFIRM + publish event
COMMIT
  AFTER_COMMIT → notification (REQUIRES_NEW)
```

## Prerequisites

1. Start Postgres: `docker compose --profile postgres up -d` (or `docker compose up -d postgres`)
2. Run the app: `./mvnw spring-boot:run -pl voyage-app`
3. Open http://localhost:8080/ui/jpa and login as an ADMIN user

API base: `/api/jpa/playground/*` (ADMIN JWT)

## Module map

| Module | Topic | Anchor code | UI / API |
|---|---|---|---|
| 01 | Entity lifecycle | `Hotel.java`, `Booking.java` (`@PrePersist`) | Lifecycle panel |
| 02 | Lazy / Eager / N+1 | `Booking` LAZY associations, `BookingRepository.findByHotelAndStatus` `@EntityGraph` | Loading panel |
| 03 | JPQL / Criteria / Specs | `BookingRepository`, `BookingCriteriaRepository`, `BookingSpec` | Query APIs panel |
| 04 | Transactions | `BookingService`, `PaymentService`, `NotificationWriter`, `InventoryService` | Transactions panel |

SQL-level isolation and `FOR UPDATE` waits also live in [`postgres-lab/`](../postgres-lab/) — compare Spring annotations here with Postgres behavior there.

---

## 01 — Entity lifecycle

States: **NEW → MANAGED → DETACHED → REMOVED**

| State | Meaning |
|---|---|
| NEW | `new Hotel(...)` — no row, no id |
| MANAGED | after `persist`/`save` inside a TX — dirty checking applies |
| DETACHED | TX ended or `EntityManager.clear()` — mutations are not flushed |
| REMOVED | after `remove` — row deleted on flush/commit |

Voyage hooks:

- `Hotel` javadoc documents the lifecycle
- `Booking.@PrePersist` stamps `createdAt` before insert

**Demo:** Lifecycle → **Persist hotel** then **Detach & mutate**. Expect: persist returns an id (MANAGED); detach mutate reports that the name change was **not** written without `merge`.

---

## 02 — Lazy loading, eager loading, N+1

- `Booking.user` / `Booking.hotel` → `FetchType.LAZY` (default for associations you should prefer)
- `RefreshToken.user` → `FetchType.EAGER` (rare exception in this codebase)
- `BookingRepository.findByHotelAndStatus` uses `@EntityGraph(attributePaths = {"hotel", "user"})` to fetch associations in one query

**N+1:** load a list of bookings, then touch `booking.getHotel().getName()` → 1 list query + N hotel selects.

**Fix:** EntityGraph / join fetch → 1 query with joins.

**Demo:** Loading → **N+1 demo** then **EntityGraph fix**. Compare `queryCount` in the output (Hibernate Statistics).

---

## 03 — JPQL, Criteria API, Specifications

Three ways to express the same booking search:

| Style | Where | When |
|---|---|---|
| JPQL `@Query` | `BookingRepository.findByUsername`, `countByHotelAndDateRange` | Fixed, readable queries |
| Criteria API | `BookingCriteriaRepository.search` | Dynamic predicates in code |
| Specifications | `BookingSpec` + `BookingService.searchWithSpecifications` | Composable filters (REST list uses this) |

**Demo:** Query APIs → run **JPQL**, **Criteria**, and **Spec** side by side. Same filters, three APIs.

---

## 04 — Transactions (booking example)

### `@Transactional` on create

`BookingService.createBooking`:

- `isolation = REPEATABLE_READ`
- `rollbackFor = PaymentFailedException`
- default propagation `REQUIRED`

Steps inside one TX:

1. Load user + hotel
2. `InventoryService.reserveRoom` per night (`SELECT … FOR UPDATE` via `RoomInventoryRepository.findForUpdate`)
3. Save `Booking` as `PENDING`
4. `PaymentService.charge` (joins the same TX — `REQUIRED`)
5. Set `CONFIRMED`, publish `BookingConfirmedEvent`
6. Commit → `NotificationService` (`AFTER_COMMIT` + `NOT_SUPPORTED`) → `NotificationWriter` (`REQUIRES_NEW`)

### Isolation

| Level | Voyage usage |
|---|---|
| READ COMMITTED | Postgres / Spring default |
| REPEATABLE READ | Booking create / cancel |

Hands-on SQL demos: postgres lab isolation buttons / `04_transactions_locks_isolation.sql`.

### Propagation

| Bean | Propagation | Why |
|---|---|---|
| `PaymentService.charge` | REQUIRED | Same booking TX — fail rolls back inventory |
| `NotificationService` listener | NOT_SUPPORTED | Run after commit outside a TX |
| `NotificationWriter` | REQUIRES_NEW | Persist notification in its own TX |

### Rollback rules

Payment token `decline` → `PaymentFailedException` → full create TX rolls back (no booking, no payment, inventory restored). Covered by `BookingServiceTransactionTest`.

### Deadlocks / lock waits

Concurrent reserves on the same inventory row contend on `FOR UPDATE`. `InventoryService` uses `@Retryable` on lock failures. Demo: **Lock contention + retry**.

**Demo path:** Transactions → seed fixtures → **Booking success** → **Booking rollback** → **Propagation map** → **Lock contention**.

---

## Suggested learning path

1. Open `/ui/jpa`, login as ADMIN.
2. Lifecycle: persist → detach mutate.
3. Loading: N+1 vs EntityGraph (watch `queryCount`).
4. Query APIs: JPQL / Criteria / Spec.
5. Transactions: success, rollback with `decline`, propagation map, lock contention.
6. Optional: `/ui/postgres` isolation demos for the SQL view of the same story.

## Tests to read

- `TransactionPolicyTest` — annotation contracts
- `BookingServiceTransactionTest` — commit vs payment rollback
- `JpaPlaygroundServiceTest` / `JpaPlaygroundSecurityTest` — lab demos + ADMIN gate
