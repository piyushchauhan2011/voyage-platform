-- Bulk seed so the planner prefers Index Scan over Seq Scan.
-- Run while the Spring app has created hotels / users / bookings tables.
-- Password for pg_lab_user: password123

BEGIN;

INSERT INTO users (username, email, password, role)
VALUES (
    'pg_lab_user',
    'pg_lab_user@voyage.local',
    '$2y$10$w00.xyAZh4K9OXGbWj5foe3SmiMpRbknMnHHyiRqOzFj0eMMpyyFa',
    'USER'
)
ON CONFLICT (username) DO NOTHING;

-- Ensure hotels 1..50 exist (hotel_id = 10 is the demo filter target)
INSERT INTO hotels (name, city, price_per_night)
SELECT
    'Lab Hotel ' || g,
    CASE (g % 5)
        WHEN 0 THEN 'Tokyo'
        WHEN 1 THEN 'Paris'
        WHEN 2 THEN 'Lisbon'
        WHEN 3 THEN 'Dubai'
        ELSE 'Copenhagen'
    END,
    80 + (g % 40) * 10
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (
    SELECT 1 FROM hotels h WHERE h.name = 'Lab Hotel ' || g
);

-- ~50k bookings across hotels (skip if already seeded heavily)
INSERT INTO bookings (
    user_id,
    hotel_id,
    room_type,
    check_in_date,
    check_out_date,
    status,
    total_price,
    created_at
)
SELECT
    (SELECT id FROM users WHERE username = 'pg_lab_user'),
    h.id,
    (ARRAY['SINGLE', 'DOUBLE', 'SUITE'])[1 + (g % 3)],
    DATE '2026-01-01' + ((g % 180) || ' days')::interval,
    DATE '2026-01-01' + ((g % 180) || ' days')::interval + INTERVAL '2 days',
    (ARRAY['PENDING', 'CONFIRMED', 'CANCELLED'])[1 + (g % 3)],
    (100 + (g % 50))::numeric,
    NOW() - ((g % 1000) || ' minutes')::interval
FROM generate_series(1, 50000) AS g
CROSS JOIN LATERAL (
    SELECT id FROM hotels WHERE name LIKE 'Lab Hotel %' ORDER BY id
    OFFSET (g % 50) LIMIT 1
) h
WHERE (SELECT COUNT(*) FROM bookings) < 10000;

COMMIT;

ANALYZE hotels;
ANALYZE bookings;

SELECT
    (SELECT COUNT(*) FROM hotels) AS hotels,
    (SELECT COUNT(*) FROM bookings) AS bookings,
    (SELECT id FROM hotels WHERE name = 'Lab Hotel 10' LIMIT 1) AS demo_hotel_id;
