package com.voyage.app.hotel;

import org.springframework.data.jpa.domain.Specification;

public final class HotelSpecifications {

    private HotelSpecifications() {
    }

    public static Specification<Hotel> hasCity(String city) {
        return (root, query, builder) -> {
            if (city == null || city.isBlank()) {
                return null;
            }
            return builder.equal(builder.lower(root.get("city")), city.toLowerCase());
        };
    }

    public static Specification<Hotel> priceAtLeast(Double minPrice) {
        return (root, query, builder) -> minPrice == null
                ? null
                : builder.greaterThanOrEqualTo(root.get("pricePerNight"), minPrice);
    }

    public static Specification<Hotel> priceAtMost(Double maxPrice) {
        return (root, query, builder) -> maxPrice == null
                ? null
                : builder.lessThanOrEqualTo(root.get("pricePerNight"), maxPrice);
    }
}