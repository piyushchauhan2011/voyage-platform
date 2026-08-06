package com.voyage.mastery.oop;

import java.util.List;

public class OopDemo {

    public static void run() {
        System.out.println("=== OOP: Polymorphism Demo ===");

        // The variable type is PaymentProvider (interface), not StripePayment/PaypalPayment (concrete).
        // At runtime, Java dispatches .pay() to the correct implementation — this is polymorphism.
        List<PaymentProvider> providers = List.of(new StripePayment(), new PaypalPayment());

        for (PaymentProvider provider : providers) {
            System.out.println("Provider: " + provider.providerName());
            PaymentResult result = provider.pay("customer_42", 199.99);
            System.out.println("  " + result);
        }
    }
}
