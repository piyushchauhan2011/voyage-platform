package com.voyage.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.voyage.app.booking.BookingConfirmedEvent;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationWriter;
import com.voyage.app.rabbitmq.LabJobMessage;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class DomainJobHandlerTest {

  @Autowired DomainJobHandler domainJobHandler;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean NotificationWriter notificationWriter;
  @MockitoBean JobRunService jobRunService;

  @Test
  void handleDomainPayload_writesNotificationAndRecordsSuccess() throws Exception {
    DomainNotificationPayload payload =
        new DomainNotificationPayload(
            "BOOKING_CONFIRMED",
            9L,
            3L,
            "alice",
            1L,
            "Hotel",
            RoomType.DOUBLE.name(),
            LocalDate.now().plusDays(1).toString(),
            LocalDate.now().plusDays(2).toString(),
            JobSource.BOOKING.name());
    LabJobMessage job =
        new LabJobMessage(
            "job-1", "email.send", objectMapper.writeValueAsString(payload), Instant.now());

    domainJobHandler.handle(job);

    ArgumentCaptor<BookingConfirmedEvent> eventCaptor =
        ArgumentCaptor.forClass(BookingConfirmedEvent.class);
    verify(notificationWriter).writeBookingConfirmed(eventCaptor.capture());
    assertThat(eventCaptor.getValue().bookingId()).isEqualTo(9L);
    verify(jobRunService)
        .record(
            "job-1", "email.send", JobSource.BOOKING, JobRunStatus.SUCCESS, job.payload(), null);
  }

  @Test
  void handlePlainPayload_recordsImmediateSuccessWithoutNotification() {
    LabJobMessage job = new LabJobMessage("job-2", "booking.confirm", "lab-only", Instant.now());

    domainJobHandler.handle(job);

    verify(jobRunService)
        .record(
            "job-2",
            "booking.confirm",
            JobSource.IMMEDIATE,
            JobRunStatus.SUCCESS,
            "lab-only",
            null);
    verify(notificationWriter, org.mockito.Mockito.never()).writeBookingConfirmed(any());
  }
}
