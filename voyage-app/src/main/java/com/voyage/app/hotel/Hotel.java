package com.voyage.app.hotel;

import jakarta.persistence.*;
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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(name = "price_per_night", nullable = false)
    private Double pricePerNight;

    public Hotel(String name, String city, Double pricePerNight) {
        this.name = name;
        this.city = city;
        this.pricePerNight = pricePerNight;
    }
}
