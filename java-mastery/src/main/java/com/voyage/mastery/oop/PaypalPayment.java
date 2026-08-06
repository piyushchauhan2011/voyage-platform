package com.voyage.mastery.oop;

import java.util.UUID;

public class PaypalPayment implements PaymentProvider {

    @Override
    public PaymentResult pay(String customerId, double amount) {
        System.out.printf("  [PayPal] Charging $%.2f for %s%n", amount, customerId);
        return PaymentResult.success("paypal_" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String providerName() {
        return "PayPal";
    }
}
