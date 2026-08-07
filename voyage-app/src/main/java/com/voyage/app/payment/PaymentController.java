package com.voyage.app.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @GetMapping("/{paymentId}")
  public PaymentResponse getPayment(@PathVariable Long paymentId) {
    return paymentService.getById(paymentId);
  }

  @GetMapping
  public PaymentResponse getPaymentByBooking(@RequestParam Long bookingId) {
    return paymentService.getByBookingId(bookingId);
  }

  @PostMapping("/{paymentId}/refund")
  public PaymentResponse refund(@PathVariable Long paymentId) {
    return paymentService.refund(paymentId);
  }
}
