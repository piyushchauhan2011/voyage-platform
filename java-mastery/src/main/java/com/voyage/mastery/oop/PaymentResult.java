package com.voyage.mastery.oop;

/**
 * Record (Java 16+) — immutable data carrier. The compiler generates constructor, getters, equals,
 * hashCode, and toString automatically.
 *
 * <p>Equivalent verbose class would be ~40 lines. Records eliminate that boilerplate.
 */
public record PaymentResult(boolean success, String transactionId, String message) {

  public static PaymentResult success(String txId) {
    return new PaymentResult(true, txId, "Payment processed");
  }

  public static PaymentResult failure(String reason) {
    return new PaymentResult(false, null, reason);
  }
}
