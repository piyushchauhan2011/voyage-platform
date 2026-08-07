package com.voyage.app.booking;

import com.voyage.app.notification.NotificationService;
import com.voyage.app.notification.NotificationWriter;
import com.voyage.app.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionPolicyTest {

    @Test
    void bookingCreationUsesRepeatableReadAndRollbackRule() throws Exception {
        Method method = BookingService.class.getMethod("createBooking", String.class, CreateBookingRequest.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertArrayEquals(new Class[]{com.voyage.app.exception.PaymentFailedException.class}, transactional.rollbackFor());
    }

    @Test
    void paymentAndNotificationUseDifferentPropagationModes() throws Exception {
        Method paymentMethod = PaymentService.class.getMethod("charge", com.voyage.app.booking.Booking.class, String.class);
        Transactional paymentTx = paymentMethod.getAnnotation(Transactional.class);
        Method writerMethod = NotificationWriter.class.getMethod("writeBookingConfirmed", BookingConfirmedEvent.class);
        Transactional writerTx = writerMethod.getAnnotation(Transactional.class);
        Method notificationMethod = NotificationService.class.getMethod("onBookingConfirmed", BookingConfirmedEvent.class);
        Transactional notificationTx = notificationMethod.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRED, paymentTx.propagation());
        assertEquals(Propagation.REQUIRES_NEW, writerTx.propagation());
        assertEquals(Propagation.NOT_SUPPORTED, notificationTx.propagation());
    }
}