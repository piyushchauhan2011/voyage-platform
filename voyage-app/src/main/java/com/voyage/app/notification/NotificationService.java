package com.voyage.app.notification;

import com.voyage.app.booking.BookingCancelledEvent;
import com.voyage.app.booking.BookingConfirmedEvent;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.jobs.DomainJobPublisher;
import com.voyage.app.jobs.DomainNotificationPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationWriter notificationWriter;
  private final ObjectProvider<DomainJobPublisher> domainJobPublisher;
  private final boolean asyncNotifications;

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationWriter notificationWriter,
      ObjectProvider<DomainJobPublisher> domainJobPublisher,
      @Value("${application.jobs.async-notifications:false}") boolean asyncNotifications) {
    this.notificationRepository = notificationRepository;
    this.notificationWriter = notificationWriter;
    this.domainJobPublisher = domainJobPublisher;
    this.asyncNotifications = asyncNotifications;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onBookingConfirmed(BookingConfirmedEvent event) {
    DomainJobPublisher publisher = domainJobPublisher.getIfAvailable();
    if (asyncNotifications && publisher != null) {
      publisher.publishBookingNotification(DomainNotificationPayload.fromConfirmed(event));
      return;
    }
    notificationWriter.writeBookingConfirmed(event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onBookingCancelled(BookingCancelledEvent event) {
    DomainJobPublisher publisher = domainJobPublisher.getIfAvailable();
    if (asyncNotifications && publisher != null) {
      publisher.publishBookingNotification(DomainNotificationPayload.fromCancelled(event));
      return;
    }
    notificationWriter.writeBookingCancelled(event);
  }

  @Transactional(readOnly = true)
  public Page<NotificationResponse> findForUser(String username, Pageable pageable) {
    return notificationRepository
        .findByUserUsernameOrderByCreatedAtDesc(username, pageable)
        .map(NotificationResponse::from);
  }

  @Transactional
  public NotificationResponse markRead(String username, Long notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Notification not found: " + notificationId));
    if (!notification.getUser().getUsername().equals(username)) {
      throw new ResourceNotFoundException("Notification not found: " + notificationId);
    }
    notification.setRead(true);
    return NotificationResponse.from(notification);
  }
}
