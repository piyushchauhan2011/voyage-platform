package com.voyage.app.notification;

import com.voyage.app.booking.BookingCancelledEvent;
import com.voyage.app.booking.BookingConfirmedEvent;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationWriter {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;

  public NotificationWriter(
      NotificationRepository notificationRepository, UserRepository userRepository) {
    this.notificationRepository = notificationRepository;
    this.userRepository = userRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeBookingConfirmed(BookingConfirmedEvent event) {
    User user =
        userRepository
            .findById(event.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + event.userId()));
    String message =
        "Booking %d confirmed for %s from %s to %s"
            .formatted(event.bookingId(), event.hotelName(), event.checkIn(), event.checkOut());
    notificationRepository.save(
        new Notification(user, event.bookingId(), NotificationType.BOOKING_CONFIRMED, message));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeBookingCancelled(BookingCancelledEvent event) {
    User user =
        userRepository
            .findById(event.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + event.userId()));
    String message =
        "Booking %d cancelled for %s from %s to %s"
            .formatted(event.bookingId(), event.hotelName(), event.checkIn(), event.checkOut());
    notificationRepository.save(
        new Notification(user, event.bookingId(), NotificationType.BOOKING_CANCELLED, message));
  }
}
