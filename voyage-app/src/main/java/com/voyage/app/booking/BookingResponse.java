package com.voyage.app.booking;

import com.voyage.app.inventory.RoomType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
    Long id,
    Long hotelId,
    String hotelName,
    String username,
    RoomType roomType,
    LocalDate checkIn,
    LocalDate checkOut,
    BookingStatus status,
    RatePlan ratePlan,
    BigDecimal totalPrice,
    Instant createdAt) {
  public static BookingResponse from(Booking booking) {
    return new BookingResponse(
        booking.getId(),
        booking.getHotel().getId(),
        booking.getHotel().getName(),
        booking.getUser().getUsername(),
        booking.getRoomType(),
        booking.getCheckIn(),
        booking.getCheckOut(),
        booking.getStatus(),
        booking.getRatePlan(),
        booking.getTotalPrice(),
        booking.getCreatedAt());
  }
}
