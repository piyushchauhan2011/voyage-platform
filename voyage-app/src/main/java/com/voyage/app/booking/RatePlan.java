package com.voyage.app.booking;

import java.math.BigDecimal;

/** Customer booking rate plan — drives price and refund eligibility (ABAC attribute on Booking). */
public enum RatePlan {
  FLEXIBLE(BigDecimal.ONE, true),
  /** 15% off; customer cannot refund on cancel. */
  NON_REFUNDABLE(new BigDecimal("0.85"), false);

  private final BigDecimal priceMultiplier;
  private final boolean refundable;

  RatePlan(BigDecimal priceMultiplier, boolean refundable) {
    this.priceMultiplier = priceMultiplier;
    this.refundable = refundable;
  }

  public BigDecimal getPriceMultiplier() {
    return priceMultiplier;
  }

  public boolean isRefundable() {
    return refundable;
  }
}
