package com.voyage.app.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long bookingId,
        NotificationType type,
        String message,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getBookingId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}