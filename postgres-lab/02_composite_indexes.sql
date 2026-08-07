-- Composite index: (hotel_id, check_in_date) vs single-column hotel_id

\set hotel_id 10

-- Filter uses leading column + second column → composite is ideal
EXPLAIN ANALYZE
SELECT id, hotel_id, check_in_date, status
FROM bookings
WHERE hotel_id = :hotel_id
  AND check_in_date BETWEEN DATE '2026-03-01' AND DATE '2026-03-31';

-- Drop composite; keep single-column — plan may still use idx_booking_hotel then filter dates
DROP INDEX IF EXISTS idx_booking_hotel_checkin;

EXPLAIN ANALYZE
SELECT id, hotel_id, check_in_date, status
FROM bookings
WHERE hotel_id = :hotel_id
  AND check_in_date BETWEEN DATE '2026-03-01' AND DATE '2026-03-31';

CREATE INDEX IF NOT EXISTS idx_booking_hotel_checkin
ON bookings (hotel_id, check_in_date);

ANALYZE bookings;

EXPLAIN ANALYZE
SELECT id, hotel_id, check_in_date, status
FROM bookings
WHERE hotel_id = :hotel_id
  AND check_in_date BETWEEN DATE '2026-03-01' AND DATE '2026-03-31';

-- User + status composite (common "my confirmed bookings" query)
EXPLAIN ANALYZE
SELECT id, status, check_in_date
FROM bookings
WHERE user_id = (SELECT id FROM users WHERE username = 'pg_lab_user')
  AND status = 'CONFIRMED';

-- Hotels by city
EXPLAIN ANALYZE
SELECT *
FROM hotels
WHERE city = 'Tokyo';
