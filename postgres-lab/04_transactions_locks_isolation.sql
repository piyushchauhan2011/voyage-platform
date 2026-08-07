-- Transactions, locks, and isolation.
-- Open TWO psql sessions (A and B) connected to voyage_db.
-- Follow steps in order. Lines marked SESSION A / SESSION B go in that window.

-- ============================================================
-- Demo 1: Pessimistic lock (FOR UPDATE) + lock wait
-- ============================================================

-- SESSION A
BEGIN;
SELECT id, available_rooms
FROM room_inventory
ORDER BY id
LIMIT 1
FOR UPDATE;
-- Keep this transaction open. Do not COMMIT yet.

-- SESSION B (will block until A commits/rolls back)
SET lock_timeout = '3s';
BEGIN;
SELECT id, available_rooms
FROM room_inventory
ORDER BY id
LIMIT 1
FOR UPDATE;
-- Expect: ERROR: canceling statement due to lock timeout
ROLLBACK;

-- SESSION A
ROLLBACK;

-- ============================================================
-- Demo 2: READ COMMITTED — non-repeatable read
-- ============================================================
-- Pick a hotel id that exists (Lab Hotel 10 or any id).

-- SESSION A
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT price_per_night FROM hotels WHERE id = 10;

-- SESSION B
BEGIN;
UPDATE hotels SET price_per_night = price_per_night + 1 WHERE id = 10;
COMMIT;

-- SESSION A (same open transaction)
SELECT price_per_night FROM hotels WHERE id = 10;
-- Second read sees B's committed change (non-repeatable read).
ROLLBACK;

-- Reset price if needed:
-- UPDATE hotels SET price_per_night = 180 WHERE id = 10;

-- ============================================================
-- Demo 3: REPEATABLE READ — snapshot stays stable
-- ============================================================

-- SESSION A
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT price_per_night FROM hotels WHERE id = 10;

-- SESSION B
BEGIN;
UPDATE hotels SET price_per_night = price_per_night + 1 WHERE id = 10;
COMMIT;

-- SESSION A
SELECT price_per_night FROM hotels WHERE id = 10;
-- Same value as first read (snapshot isolation within the transaction).
ROLLBACK;

-- ============================================================
-- App code to compare
-- ============================================================
-- BookingService create/cancel: @Transactional(isolation = REPEATABLE_READ)
-- RoomInventoryRepository.findForUpdate: @Lock(PESSIMISTIC_WRITE) → SELECT … FOR UPDATE
