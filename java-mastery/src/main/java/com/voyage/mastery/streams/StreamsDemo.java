package com.voyage.mastery.streams;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class StreamsDemo {

  // Record as a local data model — clean, no boilerplate
  record Booking(String hotelName, String city, double price, boolean confirmed) {}

  public static void run() {
    System.out.println("\n=== Streams & Lambdas Demo ===");

    List<Booking> bookings =
        List.of(
            new Booking("Grand Hotel", "Paris", 220.0, true),
            new Booking("Budget Inn", "Paris", 75.0, true),
            new Booking("Beach Resort", "Bali", 180.0, false),
            new Booking("City Center", "Tokyo", 130.0, true),
            new Booking("Mountain Lodge", "Bali", 95.0, true));

    // filter + map + collect — the core stream pipeline
    // Lambda:           b -> b.confirmed()
    // Method reference: Booking::confirmed  (same thing, just cleaner when the lambda has no logic)
    List<String> confirmedNames =
        bookings.stream()
            .filter(Booking::confirmed) // intermediate — lazy, no work yet
            .map(Booking::hotelName) // intermediate — lazy
            .collect(Collectors.toList()); // terminal — triggers execution
    System.out.println("Confirmed hotels: " + confirmedNames);

    // mapToDouble + sum — specialised stream avoids boxing overhead
    double total = bookings.stream().filter(Booking::confirmed).mapToDouble(Booking::price).sum();
    System.out.printf("Total confirmed spend: $%.2f%n", total);

    // average returns Optional because the stream might be empty
    OptionalDouble avg = bookings.stream().mapToDouble(Booking::price).average();
    avg.ifPresent(a -> System.out.printf("Average price: $%.2f%n", a));

    // groupingBy — splits stream into a Map<key, List<elements>>
    Map<String, List<Booking>> byCity =
        bookings.stream().collect(Collectors.groupingBy(Booking::city));
    System.out.println("Hotels by city:");
    byCity.forEach(
        (city, list) ->
            System.out.println(
                "  " + city + ": " + list.stream().map(Booking::hotelName).toList()));

    // flatMap — flattens nested lists into a single stream
    // Imagine each city has a list of amenities; flatMap merges them all:
    List<List<String>> amenityGroups =
        List.of(List.of("pool", "gym"), List.of("spa", "pool"), List.of("gym"));
    List<String> allAmenities =
        amenityGroups.stream().flatMap(List::stream).distinct().sorted().toList();
    System.out.println("All unique amenities: " + allAmenities);
  }
}
