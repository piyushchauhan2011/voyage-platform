package com.voyage.app.jobs;

import com.voyage.app.booking.BookingCancelledEvent;
import com.voyage.app.booking.BookingConfirmedEvent;
import com.voyage.app.notification.NotificationType;

/** JSON body carried on {@code email.send} jobs for async booking notifications. */
public record DomainNotificationPayload(
    String kind,
    Long bookingId,
    Long userId,
    String username,
    Long hotelId,
    String hotelName,
    String roomType,
    String checkIn,
    String checkOut,
    String source) {

  public static DomainNotificationPayload fromConfirmed(BookingConfirmedEvent event) {
    return new DomainNotificationPayload(
        NotificationType.BOOKING_CONFIRMED.name(),
        event.bookingId(),
        event.userId(),
        event.username(),
        event.hotelId(),
        event.hotelName(),
        event.roomType().name(),
        event.checkIn().toString(),
        event.checkOut().toString(),
        JobSource.BOOKING.name());
  }

  public static DomainNotificationPayload fromCancelled(BookingCancelledEvent event) {
    return new DomainNotificationPayload(
        NotificationType.BOOKING_CANCELLED.name(),
        event.bookingId(),
        event.userId(),
        event.username(),
        event.hotelId(),
        event.hotelName(),
        event.roomType().name(),
        event.checkIn().toString(),
        event.checkOut().toString(),
        JobSource.BOOKING.name());
  }
}
