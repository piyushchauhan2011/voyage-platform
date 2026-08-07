package com.voyage.mastery.collections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CollectionsDemo {

  public static void run() {
    System.out.println("\n=== Collections Demo ===");
    hashMapDemo();
    arrayListVsLinkedListDemo();
    priorityQueueDemo();
  }

  private static void hashMapDemo() {
    System.out.println("\n-- HashMap internals --");
    // key.hashCode() → bucket index → linked list / tree within bucket → equals() finds entry
    // Default capacity: 16 buckets. Resizes (doubles) when load factor 0.75 is exceeded.
    Map<String, Integer> prices = new HashMap<>();
    prices.put("standard", 80);
    prices.put("deluxe", 150);
    prices.put("suite", 300);
    prices.put(null, 50); // HashMap stores null key in bucket 0 — only one allowed

    System.out.println("Prices: " + prices);
    System.out.println("getOrDefault missing key: $" + prices.getOrDefault("penthouse", -1));

    // ConcurrentHashMap: thread-safe, segments the map to reduce lock contention.
    // Does NOT allow null keys or values (null is ambiguous in concurrent access).
    Map<String, Integer> concurrent =
        new ConcurrentHashMap<>(
            prices.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    System.out.println("ConcurrentHashMap (no null): " + concurrent);
  }

  private static void arrayListVsLinkedListDemo() {
    System.out.println("\n-- ArrayList vs LinkedList --");
    // ArrayList: contiguous array in memory.
    //   get(i) → O(1) — direct index lookup
    //   add/remove at middle → O(n) — must shift elements
    List<String> hotels = new ArrayList<>(List.of("hotel-a", "hotel-b", "hotel-c"));
    hotels.add(1, "hotel-x"); // shifts hotel-b and hotel-c right
    System.out.println("ArrayList after mid-insert: " + hotels);

    // LinkedList as Deque: O(1) insert/remove at both ends, O(n) random access.
    // Use as a queue or stack, not for indexed access.
    Deque<String> queue = new LinkedList<>(List.of("booking-1", "booking-2"));
    queue.addFirst("booking-0");
    queue.addLast("booking-3");
    System.out.println("LinkedList deque: " + queue);
    System.out.println("  poll (removes head): " + queue.poll());
    System.out.println("  peek (reads head):   " + queue.peek());
  }

  private static void priorityQueueDemo() {
    System.out.println("\n-- PriorityQueue (min-heap) --");
    // poll() always returns the smallest element regardless of insertion order.
    // Internally a binary heap: O(log n) offer/poll, O(1) peek.
    PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(300, 80, 150, 50, 200));
    System.out.print("Prices cheapest-first: ");
    while (!pq.isEmpty()) {
      System.out.print("$" + pq.poll() + " ");
    }
    System.out.println();
  }
}
