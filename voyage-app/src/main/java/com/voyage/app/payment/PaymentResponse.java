package com.voyage.app.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long id,
    Long bookingId,
    Long hotelId,
    BigDecimal amount,
    PaymentStatus status,
    PaymentProvider provider,
    String transactionReference,
    Instant processedAt) {
  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getBooking().getId(),
        payment.getBooking().getHotel().getId(),
        payment.getAmount(),
        payment.getStatus(),
        payment.getProvider(),
        payment.getTransactionReference(),
        payment.getProcessedAt());
  }
}
