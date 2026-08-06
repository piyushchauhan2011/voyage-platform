package com.voyage.mastery.oop;

/**
 * Interface defines the contract — not the implementation.
 *
 * Key OOP principle (Dependency Inversion):
 *   BAD:  PaymentService depends on StripePayment (concrete class)
 *   GOOD: PaymentService depends on PaymentProvider (interface)
 *
 * This means you can swap Stripe for PayPal without changing PaymentService.
 * Spring's IoC container is built entirely on this idea.
 */
public interface PaymentProvider {

    PaymentResult pay(String customerId, double amount);

    String providerName();
}
