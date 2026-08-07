# Postgres Skills Lab

Hands-on SQL for indexes, the query planner, partitioning, locks, and isolation — using Voyage's `bookings` / `hotels` tables plus a dedicated partitioned demo table.

## Prerequisites

1. Start Postgres: `docker compose up -d postgres`
2. Start the app once so Hibernate creates the core schema: `./mvnw spring-boot:run -pl voyage-app`
3. Leave the app running, or keep the DB volume so tables remain after you stop it (`create-drop` drops schema on shutdown — re-run the app before seeding if needed)

## Connect

```bash
docker exec -it voyage-postgres psql -U voyage -d voyage_db
```

Paste script contents into `psql`, or copy files into the container:

```bash
docker cp postgres-lab/. voyage-postgres:/tmp/postgres-lab/
docker exec -it voyage-postgres psql -U voyage -d voyage_db -f /tmp/postgres-lab/00_seed_volume.sql
```

## Script order

| File | Topic |
|---|---|
| `00_seed_volume.sql` | Bulk hotels + bookings (enough rows for Index Scan) |
| `01_indexes_explain.sql` | Seq Scan vs Index Scan with `EXPLAIN ANALYZE` |
| `02_composite_indexes.sql` | Single-column vs composite indexes |
| `03_partitioning.sql` | RANGE partitions + partition pruning |
| `04_transactions_locks_isolation.sql` | `FOR UPDATE`, lock waits, isolation levels |

## App playground (same labs via API/UI)

- UI: http://localhost:8080/ui/postgres
- API: `/api/postgres/playground/*` (ADMIN JWT)

See the root README **PostgreSQL lab** section for curl examples.

For Spring `@Transactional`, N+1, and the booking TX story on the same domain model, see [`jpa-lab/`](../jpa-lab/) and `/ui/jpa`.
