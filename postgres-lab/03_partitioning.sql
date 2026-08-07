-- RANGE partitioning demo on a dedicated table (not managed by Hibernate).

DROP TABLE IF EXISTS pg_lab_bookings CASCADE;

CREATE TABLE pg_lab_bookings (
    id            BIGSERIAL,
    hotel_id      BIGINT NOT NULL,
    check_in_date DATE NOT NULL,
    status        TEXT NOT NULL,
    total_price   NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (id, check_in_date)
) PARTITION BY RANGE (check_in_date);

CREATE TABLE pg_lab_bookings_2026_q1 PARTITION OF pg_lab_bookings
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');

CREATE TABLE pg_lab_bookings_2026_q2 PARTITION OF pg_lab_bookings
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');

CREATE TABLE pg_lab_bookings_2026_q3 PARTITION OF pg_lab_bookings
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');

CREATE TABLE pg_lab_bookings_2026_q4 PARTITION OF pg_lab_bookings
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

INSERT INTO pg_lab_bookings (hotel_id, check_in_date, status, total_price)
SELECT
    1 + (g % 20),
    DATE '2026-01-01' + ((g % 365) || ' days')::interval,
    (ARRAY['PENDING', 'CONFIRMED', 'CANCELLED'])[1 + (g % 3)],
    (80 + (g % 100))::numeric
FROM generate_series(1, 20000) AS g;

ANALYZE pg_lab_bookings;

-- Partition pruning: only Q1 child should be scanned
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM pg_lab_bookings
WHERE check_in_date >= DATE '2026-01-15'
  AND check_in_date < DATE '2026-02-15';

-- Confirm which partitions hold rows
SELECT
    tableoid::regclass AS partition,
    COUNT(*) AS rows
FROM pg_lab_bookings
GROUP BY 1
ORDER BY 1;
