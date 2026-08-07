-- Indexes + EXPLAIN ANALYZE on real bookings.
-- Pick the hotel_id printed by 00_seed_volume.sql (Lab Hotel 10), or use 10 if ids match.

\set hotel_id 10

-- Bad pattern (no usable index after DROP below): full table scan risk
EXPLAIN ANALYZE
SELECT *
FROM bookings
WHERE hotel_id = :hotel_id;

-- Drop the lab index to force a worse plan (safe to re-create afterward)
DROP INDEX IF EXISTS idx_booking_hotel;

EXPLAIN ANALYZE
SELECT *
FROM bookings
WHERE hotel_id = :hotel_id;

-- Better: restore the index from Booking entity / lab
CREATE INDEX IF NOT EXISTS idx_booking_hotel
ON bookings (hotel_id);

ANALYZE bookings;

EXPLAIN ANALYZE
SELECT *
FROM bookings
WHERE hotel_id = :hotel_id;

-- Look for: Seq Scan vs Index Scan / Bitmap Index Scan on idx_booking_hotel
-- Compare "Execution Time" before and after the index.
