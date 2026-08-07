package com.voyage.mastery.jvm;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM internals — senior interview essentials.
 *
 * Pipeline:
 *   .java source  →  javac  →  .class bytecode  →  JVM (class loader + interpreter/JIT)  →  machine code
 *
 * Memory regions the JVM manages:
 *   Heap      — objects / arrays (shared across threads); subject to GC
 *   Stack     — per-thread frames: locals, operand stack, return address (method calls)
 *   Metaspace — class metadata, method bytecode, constant pools (native memory, not the Java heap)
 */
public class JvmDemo {

    public static void run() {
        System.out.println("\n=== JVM Internals Demo ===");
        classLoadingDemo();
        memoryRegionsDemo();
        heapGenerationsDemo();
        garbageCollectorsDemo();
        allocationChurnDemo();
        gcFlagsCheatSheet();
    }

    private static void classLoadingDemo() {
        System.out.println("\n-- .class file & class loading --");
        // Each .class holds: constant pool, fields, methods, bytecode instructions.
        // Class loaders pull bytecode into Metaspace and link/initialize the Class object.
        Class<?> clazz = JvmDemo.class;
        ClassLoader loader = clazz.getClassLoader();
        System.out.println("Class name:     " + clazz.getName());
        System.out.println("Simple name:    " + clazz.getSimpleName());
        System.out.println("ClassLoader:    " + loader); // AppClassLoader for app classes
        System.out.println("String loader:  " + String.class.getClassLoader()); // bootstrap → null
        // Bootstrap (null) → Platform → Application (AppClassLoader) — parent-first delegation.
    }

    private static void memoryRegionsDemo() {
        System.out.println("\n-- Memory regions (Heap / Stack / Metaspace) --");
        Runtime rt = Runtime.getRuntime();
        System.out.printf("Heap max:   %d MB%n", rt.maxMemory() / (1024 * 1024));
        System.out.printf("Heap total: %d MB%n", rt.totalMemory() / (1024 * 1024));
        System.out.printf("Heap free:  %d MB%n", rt.freeMemory() / (1024 * 1024));
        System.out.println("Available processors: " + rt.availableProcessors());
        // Heap:   shared object store — sized with -Xms / -Xmx
        // Stack:  one per thread — sized with -Xss; StackOverflowError if too deep
        // Metaspace: class metadata — sized with -XX:MaxMetaspaceSize (native, not -Xmx)
    }

    private static void heapGenerationsDemo() {
        System.out.println("\n-- Heap generations --");
        // Most objects die young (generational hypothesis). Collectors exploit that:
        //
        //   Young generation
        //     Eden        — new allocations land here
        //     Survivor S0/S1 — objects that survived one young GC flip between these
        //   Old (Tenured) — objects that survived enough young collections are promoted here
        //
        // Minor GC: young only (fast). Major/Full GC: includes old (slower, depends on collector).
        System.out.println("Eden → Survivor (age++) → Old generation after threshold");
        System.out.println("Minor GC clears Eden; survivors promote or flip to the other Survivor space");
    }

    private static void garbageCollectorsDemo() {
        System.out.println("\n-- Garbage collectors --");
        // Parallel GC  — stop-the-world, multi-threaded; maximize throughput (batch jobs)
        // G1 GC        — default on modern HotSpot; region-based, pause-goal oriented (most services)
        // ZGC          — ultra-low pause (sub-ms targets); large heaps, latency-sensitive APIs
        //
        // Interview pick:
        //   throughput / batch     → Parallel
        //   balanced web service   → G1 (default)
        //   p99 latency / big heap → ZGC
        String name = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(java.lang.management.GarbageCollectorMXBean::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");
        System.out.println("Active collectors on this JVM: " + name);
    }

    private static void allocationChurnDemo() {
        System.out.println("\n-- Young-gen churn (allocate short-lived objects) --");
        Runtime rt = Runtime.getRuntime();
        long freeBefore = rt.freeMemory();
        // Short-lived objects → Eden → usually die before promotion (ideal GC workload).
        List<byte[]> junk = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            junk.add(new byte[1024]); // 1 KB each ≈ 5 MB of ephemeral garbage
        }
        junk.clear(); // make them unreachable — eligible for next young GC
        long freeAfter = rt.freeMemory();
        System.out.printf("Free before churn: %d KB%n", freeBefore / 1024);
        System.out.printf("Free after  churn: %d KB (GC may reclaim asynchronously)%n", freeAfter / 1024);
    }

    private static void gcFlagsCheatSheet() {
        System.out.println("\n-- GC / memory flags (cheat sheet) --");
        System.out.println("  -Xms512m -Xmx512m          initial & max heap (pin size to avoid resize)");
        System.out.println("  -XX:+UseG1GC               G1 (balanced — default on recent JDKs)");
        System.out.println("  -XX:+UseZGC                ZGC (low-latency)");
        System.out.println("  -XX:+UseParallelGC         Parallel (throughput)");
        System.out.println("  -XX:MaxMetaspaceSize=256m  cap class metadata");
        System.out.println("  -Xlog:gc*                  GC logging (JDK 9+ unified logging)");
        System.out.println("  -Xss1m                     per-thread stack size");
    }
}
