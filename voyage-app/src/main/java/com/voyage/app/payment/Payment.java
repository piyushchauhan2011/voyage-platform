package com.voyage.app.payment;

import com.voyage.app.booking.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false, unique = true)
  private Booking booking;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentProvider provider;

  @Column(name = "transaction_reference", nullable = false, unique = true)
  private String transactionReference;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  public Payment(
      Booking booking,
      BigDecimal amount,
      PaymentStatus status,
      PaymentProvider provider,
      String transactionReference) {
    this.booking = booking;
    this.amount = amount;
    this.status = status;
    this.provider = provider;
    this.transactionReference = transactionReference;
  }

  @PrePersist
  void markProcessedAt() {
    if (processedAt == null) {
      processedAt = Instant.now();
    }
  }
}
