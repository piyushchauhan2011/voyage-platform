package com.voyage.mastery.oop;

import java.util.UUID;

public class StripePayment implements PaymentProvider {

    @Override
    public PaymentResult pay(String customerId, double amount) {
        System.out.printf("  [Stripe] Charging $%.2f for %s%n", amount, customerId);
        return PaymentResult.success("stripe_" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String providerName() {
        return "Stripe";
    }
}
