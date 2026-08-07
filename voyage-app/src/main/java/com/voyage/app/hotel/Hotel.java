package com.voyage.app.hotel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.voyage.app.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity — maps this class to the "hotels" table.
 *
 * <p>Hibernate reads @Entity classes and generates DDL (with ddl-auto=create-drop). Lifecycle: NEW
 * → MANAGED (after persist) → DETACHED (after transaction ends) → REMOVED
 */
@Entity
@Table(
    name = "hotels",
    indexes = {
      @Index(name = "idx_hotel_city", columnList = "city"),
      @Index(name = "idx_hotel_manager", columnList = "manager_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class Hotel {

  // IDENTITY delegates ID generation to the database column (SERIAL / BIGSERIAL in Postgres)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(nullable = false)
  private String name;

  @NotBlank
  @Column(nullable = false)
  private String city;

  @NotNull
  @Positive
  @Column(name = "price_per_night", nullable = false)
  private Double pricePerNight;

  /**
   * Phase 8 — free text describing location, vibe, and nearby landmarks. This is what gets embedded
   * into pgvector, so a query like "near the beach" can match a hotel that never uses the word
   * "beach" in its name or city. Nullable so hotels created before Phase 8 (and the existing API)
   * stay valid.
   */
  @Column(length = 2000)
  private String description;

  /** Comma-separated list — kept simple on purpose; it is embedded alongside the description. */
  @Column(length = 500)
  private String amenities;

  /**
   * Hotel manager who owns this property (ABAC attribute). Null when created by an admin without
   * assignment.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_id")
  @JsonIgnore
  private User manager;

  /** SaaS subscription plan — ABAC attribute gating inventory writes and refunds. */
  @Enumerated(EnumType.STRING)
  @Column(name = "saas_plan", nullable = false)
  private SaasPlan saasPlan = SaasPlan.FREE;

  public Hotel(String name, String city, Double pricePerNight) {
    this.name = name;
    this.city = city;
    this.pricePerNight = pricePerNight;
    this.saasPlan = SaasPlan.FREE;
  }

  public Hotel(
      String name, String city, Double pricePerNight, String description, String amenities) {
    this(name, city, pricePerNight);
    this.description = description;
    this.amenities = amenities;
  }

  @JsonProperty("managerId")
  public Long getManagerId() {
    return manager != null ? manager.getId() : null;
  }

  @JsonProperty("managerUsername")
  public String getManagerUsername() {
    return manager != null ? manager.getUsername() : null;
  }
}
