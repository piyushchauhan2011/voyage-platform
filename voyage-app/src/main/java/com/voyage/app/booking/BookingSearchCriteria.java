package com.voyage.app.booking;

import java.time.LocalDate;
import java.util.List;

public record BookingSearchCriteria(
    Long userId,
    Long hotelId,
    List<Long> hotelIds,
    BookingStatus status,
    LocalDate checkInFrom,
    LocalDate checkInTo) {
  public BookingSearchCriteria(
      Long userId, Long hotelId, BookingStatus status, LocalDate checkInFrom, LocalDate checkInTo) {
    this(userId, hotelId, null, status, checkInFrom, checkInTo);
  }
}
