package com.voyage.app.booking;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class BookingSpec {

  private BookingSpec() {}

  public static Specification<Booking> hasStatus(BookingStatus status) {
    return (root, query, builder) ->
        status == null ? null : builder.equal(root.get("status"), status);
  }

  public static Specification<Booking> forUser(Long userId) {
    return (root, query, builder) ->
        userId == null ? null : builder.equal(root.get("user").get("id"), userId);
  }

  public static Specification<Booking> forHotel(Long hotelId) {
    return (root, query, builder) ->
        hotelId == null ? null : builder.equal(root.get("hotel").get("id"), hotelId);
  }

  public static Specification<Booking> forHotels(java.util.Collection<Long> hotelIds) {
    return (root, query, builder) -> {
      if (hotelIds == null || hotelIds.isEmpty()) {
        return null;
      }
      return root.get("hotel").get("id").in(hotelIds);
    };
  }

  public static Specification<Booking> checkInAfter(LocalDate date) {
    return (root, query, builder) ->
        date == null ? null : builder.greaterThanOrEqualTo(root.get("checkIn"), date);
  }

  public static Specification<Booking> checkInBefore(LocalDate date) {
    return (root, query, builder) ->
        date == null ? null : builder.lessThanOrEqualTo(root.get("checkIn"), date);
  }
}
