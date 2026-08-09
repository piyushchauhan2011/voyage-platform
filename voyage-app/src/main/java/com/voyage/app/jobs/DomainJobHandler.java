package com.voyage.app.jobs;

import com.voyage.app.booking.BookingCancelledEvent;
import com.voyage.app.booking.BookingConfirmedEvent;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationType;
import com.voyage.app.notification.NotificationWriter;
import com.voyage.app.rabbitmq.LabJobMessage;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DomainJobHandler {

  private final JobRunService jobRunService;
  private final NotificationWriter notificationWriter;
  private final ObjectMapper objectMapper;

  public DomainJobHandler(
      JobRunService jobRunService,
      NotificationWriter notificationWriter,
      ObjectMapper objectMapper) {
    this.jobRunService = jobRunService;
    this.notificationWriter = notificationWriter;
    this.objectMapper = objectMapper;
  }

  public void handle(LabJobMessage job) {
    JobSource source = JobSource.IMMEDIATE;
    try {
      ParsedPayload parsed = parse(job.payload());
      source = parsed.source();
      if (parsed.notification() != null) {
        writeNotification(parsed.notification());
      }
      jobRunService.record(
          job.jobId(), job.type(), source, JobRunStatus.SUCCESS, job.payload(), null);
    } catch (RuntimeException ex) {
      jobRunService.record(
          job.jobId(),
          job.type(),
          source,
          JobRunStatus.FAILED,
          job.payload(),
          truncate(ex.getMessage()));
      throw ex;
    }
  }

  private void writeNotification(DomainNotificationPayload payload) {
    NotificationType type = NotificationType.valueOf(payload.kind());
    if (type == NotificationType.BOOKING_CONFIRMED) {
      notificationWriter.writeBookingConfirmed(
          new BookingConfirmedEvent(
              payload.bookingId(),
              payload.userId(),
              payload.username(),
              payload.hotelId(),
              payload.hotelName(),
              RoomType.valueOf(payload.roomType()),
              LocalDate.parse(payload.checkIn()),
              LocalDate.parse(payload.checkOut())));
    } else {
      notificationWriter.writeBookingCancelled(
          new BookingCancelledEvent(
              payload.bookingId(),
              payload.userId(),
              payload.username(),
              payload.hotelId(),
              payload.hotelName(),
              RoomType.valueOf(payload.roomType()),
              LocalDate.parse(payload.checkIn()),
              LocalDate.parse(payload.checkOut())));
    }
  }

  private ParsedPayload parse(String payload) {
    if (payload == null || payload.isBlank() || !payload.trim().startsWith("{")) {
      return new ParsedPayload(JobSource.IMMEDIATE, null);
    }
    try {
      JsonNode node = objectMapper.readTree(payload);
      if (node.has("kind") && node.has("bookingId")) {
        DomainNotificationPayload notification =
            objectMapper.treeToValue(node, DomainNotificationPayload.class);
        JobSource source =
            notification.source() == null
                ? JobSource.BOOKING
                : JobSource.valueOf(notification.source());
        return new ParsedPayload(source, notification);
      }
      if (node.has("source") && node.has("text")) {
        JobSource source = JobSource.valueOf(node.get("source").asString());
        return new ParsedPayload(source, null);
      }
      if (node.has("source")) {
        return new ParsedPayload(JobSource.valueOf(node.get("source").asString()), null);
      }
    } catch (Exception ignored) {
      // Lab plain / unknown JSON — still record a successful consume.
    }
    return new ParsedPayload(JobSource.IMMEDIATE, null);
  }

  private static String truncate(String message) {
    if (message == null) {
      return "error";
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private record ParsedPayload(JobSource source, DomainNotificationPayload notification) {}
}
