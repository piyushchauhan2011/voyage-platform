package com.voyage.mastery.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multithreading primitives — Thread, pools, futures, locks, volatile, atomics. See
 * DoubleBookingDemo for the senior "1000 users, one room" race.
 */
public class ConcurrencyDemo {

  public static void run() {
    System.out.println("\n=== Concurrency Demo ===");
    threadAndRunnableDemo();
    callableAndFutureDemo();
    completableFutureDemo();
    executorAndForkJoinDemo();
    synchronizedVsLockDemo();
    volatileDemo();
    atomicVsPlainDemo();
  }

  private static void threadAndRunnableDemo() {
    System.out.println("\n-- Thread / Runnable --");
    // Runnable = work unit (no return). Thread = OS/JVM execution vehicle.
    List<String> log = new ArrayList<>();
    Thread t =
        new Thread(
            () -> log.add("booked-by-" + Thread.currentThread().getName()), "booking-worker");
    t.start();
    try {
      t.join(); // wait until worker finishes
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    System.out.println("After join: " + log);
  }

  private static void callableAndFutureDemo() {
    System.out.println("\n-- Callable + Future (ExecutorService) --");
    // Callable<V> returns a value and may throw checked exceptions.
    // Future<V> is a handle to a result that may not be ready yet.
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> priceLookup =
          () -> {
            Thread.sleep(50);
            return 199; // suite nightly rate
          };
      Future<Integer> future = pool.submit(priceLookup);
      System.out.println("Future done? " + future.isDone() + " (submitted, not joined yet)");
      Integer price = future.get(); // blocks until result
      System.out.println("Suite price from Callable: $" + price);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    } finally {
      pool.shutdown();
    }
  }

  private static void completableFutureDemo() {
    System.out.println("\n-- CompletableFuture --");
    // Non-blocking composition: thenApply transforms, thenCombine merges two async results.
    CompletableFuture<String> hotel = CompletableFuture.supplyAsync(() -> "Grand Hotel");
    CompletableFuture<Integer> nights = CompletableFuture.supplyAsync(() -> 3);

    String summary =
        hotel
            .thenCombine(nights, (name, n) -> name + " × " + n + " nights")
            .thenApply(s -> s + " — confirmed")
            .join(); // block only at the end for the demo
    System.out.println(summary);
  }

  private static void executorAndForkJoinDemo() {
    System.out.println("\n-- ExecutorService vs ForkJoinPool --");
    // ExecutorService: you submit independent tasks (I/O, request handling).
    // ForkJoinPool: work-stealing for divide-and-conquer / parallel streams (CPU-bound).
    ExecutorService fixed = Executors.newFixedThreadPool(4);
    System.out.println("Fixed pool (4 workers) — good for bounded request concurrency");
    fixed.shutdown();

    ForkJoinPool common = ForkJoinPool.commonPool();
    System.out.println("ForkJoinPool.commonPool parallelism: " + common.getParallelism());
    // parallelStream() and many CompletableFuture defaults use the common pool.
  }

  private static void synchronizedVsLockDemo() {
    System.out.println("\n-- synchronized vs ReentrantLock --");
    // synchronized: intrinsic monitor — simple, auto-unlock on exit / exception.
    // ReentrantLock: explicit lock API — tryLock, fairness, multiple conditions.
    Object monitor = new Object();
    synchronized (monitor) {
      System.out.println("Inside synchronized block — exclusive access to shared booking state");
    }

    ReentrantLock lock = new ReentrantLock();
    lock.lock();
    try {
      System.out.println("Inside ReentrantLock — same mutual exclusion, more control");
    } finally {
      lock.unlock(); // always unlock in finally
    }
  }

  private static volatile boolean stopFlag;

  private static void volatileDemo() {
    System.out.println("\n-- volatile (visibility, not atomicity) --");
    // volatile guarantees reads see the latest write across threads (happens-before).
    // It does NOT make compound actions (i++, check-then-act) atomic — use locks/atomics for that.
    stopFlag = false;
    Thread reader =
        new Thread(
            () -> {
              while (!stopFlag) {
                // spin until writer flips the volatile flag
                Thread.onSpinWait();
              }
              System.out.println("Reader saw volatile stop flag");
            },
            "flag-reader");
    reader.start();
    try {
      Thread.sleep(20);
      stopFlag = true; // volatile write is visible to the reader
      reader.join(1_000);
      if (reader.isAlive()) {
        reader.interrupt();
        System.out.println("Reader timed out (unexpected with volatile)");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    System.out.println(
        "Without volatile, HotSpot may cache the flag in a register → infinite spin");
  }

  private static void atomicVsPlainDemo() {
    System.out.println("\n-- AtomicInteger vs plain int++ --");
    // int++ is read-modify-write — races under concurrency.
    // AtomicInteger uses CAS (compare-and-swap) for lock-free updates.
    final int[] plain = {0};
    AtomicInteger atomic = new AtomicInteger(0);
    int threads = 8;
    int increments = 10_000;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            for (int j = 0; j < increments; j++) {
              plain[0]++; // racy
              atomic.incrementAndGet(); // safe
            }
          });
    }
    pool.shutdown();
    try {
      pool.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    int expected = threads * increments;
    System.out.println("Expected:        " + expected);
    System.out.println("Plain int++:     " + plain[0] + " (usually under-counts)");
    System.out.println("AtomicInteger:   " + atomic.get() + " (exact)");
  }
}
