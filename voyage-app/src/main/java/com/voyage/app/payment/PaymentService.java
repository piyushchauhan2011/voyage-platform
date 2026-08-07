package com.voyage.app.payment;

import com.voyage.app.booking.Booking;
import com.voyage.app.exception.ConflictException;
import com.voyage.app.exception.PaymentFailedException;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.security.HotelAccessService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final HotelAccessService hotelAccessService;

  public PaymentService(
      PaymentRepository paymentRepository, HotelAccessService hotelAccessService) {
    this.paymentRepository = paymentRepository;
    this.hotelAccessService = hotelAccessService;
  }

  @Transactional
  public Payment charge(Booking booking, String paymentToken) {
    if (paymentToken != null && paymentToken.equalsIgnoreCase("decline")) {
      throw new PaymentFailedException("Payment was declined by the mock provider");
    }

    Payment payment =
        new Payment(
            booking,
            booking.getTotalPrice(),
            PaymentStatus.SUCCEEDED,
            PaymentProvider.MOCK,
            "pay_" + UUID.randomUUID());
    return paymentRepository.save(payment);
  }

  @Transactional(readOnly = true)
  public PaymentResponse getById(Long paymentId) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    hotelAccessService.assertCanViewPayment(payment);
    return PaymentResponse.from(payment);
  }

  @Transactional(readOnly = true)
  public PaymentResponse getByBookingId(Long bookingId) {
    Payment payment =
        paymentRepository
            .findByBookingId(bookingId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment not found for booking: " + bookingId));
    hotelAccessService.assertCanViewPayment(payment);
    return PaymentResponse.from(payment);
  }

  @Transactional
  public PaymentResponse refund(Long paymentId) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    hotelAccessService.assertCanRefund(payment);
    return PaymentResponse.from(applyRefund(payment));
  }

  /**
   * Internal refund used by booking cancel — skips ABAC (caller already authorized the cancel).
   * No-ops if already refunded or payment is not SUCCEEDED.
   */
  @Transactional
  public Payment refundIfSucceeded(Long bookingId) {
    return paymentRepository
        .findByBookingId(bookingId)
        .filter(payment -> payment.getStatus() == PaymentStatus.SUCCEEDED)
        .map(this::applyRefund)
        .orElse(null);
  }

  private Payment applyRefund(Payment payment) {
    if (payment.getStatus() == PaymentStatus.REFUNDED) {
      return payment;
    }
    if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
      throw new ConflictException(
          "Only SUCCEEDED payments can be refunded (status=" + payment.getStatus() + ")");
    }
    payment.setStatus(PaymentStatus.REFUNDED);
    payment.setTransactionReference(payment.getTransactionReference() + "_refund");
    return paymentRepository.save(payment);
  }
}
