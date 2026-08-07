package com.voyage.app.payment;

import com.voyage.app.booking.Booking;
import com.voyage.app.exception.PaymentFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment charge(Booking booking, String paymentToken) {
        if (paymentToken != null && paymentToken.equalsIgnoreCase("decline")) {
            throw new PaymentFailedException("Payment was declined by the mock provider");
        }

        Payment payment = new Payment(
                booking,
                booking.getTotalPrice(),
                PaymentStatus.SUCCEEDED,
                PaymentProvider.MOCK,
                "pay_" + UUID.randomUUID()
        );
        return paymentRepository.save(payment);
    }
}