package com.voyage.mastery.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Senior interview classic: 1000 users try to book the last room at the same time.
 *
 * Production sequel in voyage-app (DB-level, not in-memory):
 *   RoomInventoryRepository.findForUpdate — @Lock(PESSIMISTIC_WRITE) / SELECT … FOR UPDATE
 *   InventoryService.reserveRoom — decrements under that row lock inside @Transactional
 */
public class DoubleBookingDemo {

    private static final int BOOKERS = 1000;

    public static void run() {
        System.out.println("\n=== Double Booking Demo (1000 users, 1 room) ===");
        runVariant("BROKEN  (unsynchronized lost update)", new BrokenInventory());
        runVariant("FIXED A (synchronized)", new SynchronizedInventory());
        runVariant("FIXED B (ReentrantLock)", new LockedInventory());
        runVariant("FIXED C (AtomicInteger CAS)", new AtomicInventory());
        System.out.println("\nTakeaway: check-then-act on shared inventory needs mutual exclusion or CAS.");
        System.out.println("In voyage-app the same race is prevented with pessimistic DB locks + transactions.");
    }

    private static void runVariant(String label, Inventory inventory) {
        AtomicInteger successes = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BOOKERS);
        ExecutorService pool = Executors.newFixedThreadPool(64);

        for (int i = 0; i < BOOKERS; i++) {
            pool.submit(() -> {
                try {
                    start.await(); // release all bookers together → maximize the race
                    if (inventory.tryBook()) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdown();

        int booked = successes.get();
        int remaining = inventory.roomsLeft();
        String verdict = (booked == 1 && remaining == 0) ? "OK" : "OVERSOLD / RACE";
        System.out.printf("%s → successes=%d remaining=%d [%s]%n", label, booked, remaining, verdict);
    }

    /** Shared contract: one room left at construction. */
    private interface Inventory {
        boolean tryBook();
        int roomsLeft();
    }

    /** Unsynchronized check-then-act — classic double-booking race. */
    private static final class BrokenInventory implements Inventory {
        private int roomsLeft = 1;

        @Override
        public boolean tryBook() {
            // Lost update: many threads read the same snapshot, then all write snapshot-1.
            int snapshot = roomsLeft;
            try {
                Thread.sleep(2); // widen the race window for a visible oversell
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (snapshot > 0) {
                roomsLeft = snapshot - 1;
                return true;
            }
            return false;
        }

        @Override
        public int roomsLeft() {
            return roomsLeft;
        }
    }

    /** Intrinsic lock on the inventory instance. */
    private static final class SynchronizedInventory implements Inventory {
        private int roomsLeft = 1;

        @Override
        public synchronized boolean tryBook() {
            if (roomsLeft > 0) {
                roomsLeft--;
                return true;
            }
            return false;
        }

        @Override
        public synchronized int roomsLeft() {
            return roomsLeft;
        }
    }

    /** Explicit ReentrantLock — same exclusivity, tryLock / fairness options available. */
    private static final class LockedInventory implements Inventory {
        private final ReentrantLock lock = new ReentrantLock();
        private int roomsLeft = 1;

        @Override
        public boolean tryBook() {
            lock.lock();
            try {
                if (roomsLeft > 0) {
                    roomsLeft--;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int roomsLeft() {
            lock.lock();
            try {
                return roomsLeft;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Lock-free CAS: only one thread can transition 1 → 0 successfully.
     * {@link AtomicInteger#decrementAndGet()} alone is wrong here — it would go negative.
     */
    private static final class AtomicInventory implements Inventory {
        private final AtomicInteger roomsLeft = new AtomicInteger(1);

        @Override
        public boolean tryBook() {
            // Spin on CAS until we either take the last room or see zero.
            while (true) {
                int current = roomsLeft.get();
                if (current <= 0) {
                    return false;
                }
                if (roomsLeft.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        @Override
        public int roomsLeft() {
            return roomsLeft.get();
        }
    }
}
