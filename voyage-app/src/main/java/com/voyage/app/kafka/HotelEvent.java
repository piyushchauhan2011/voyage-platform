package com.voyage.app.kafka;

import java.time.Instant;

public record HotelEvent(
    String eventId,
    int schemaVersion,
    HotelEventType eventType,
    Long hotelId,
    String hotelName,
    String city,
    Double pricePerNight,
    Instant occurredAt) {}
