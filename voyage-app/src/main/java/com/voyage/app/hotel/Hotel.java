package com.voyage.app.hotel;

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
 * Hibernate reads @Entity classes and generates DDL (with ddl-auto=create-drop).
 * Lifecycle:  NEW → MANAGED (after persist) → DETACHED (after transaction ends) → REMOVED
 */
@Entity
@Table(name = "hotels")
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

    public Hotel(String name, String city, Double pricePerNight) {
        this.name = name;
        this.city = city;
        this.pricePerNight = pricePerNight;
    }
}
