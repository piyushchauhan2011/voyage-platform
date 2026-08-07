package com.voyage.app.booking;

import java.time.LocalDate;

public record BookingSearchCriteria(
        Long userId,
        Long hotelId,
        BookingStatus status,
        LocalDate checkInFrom,
        LocalDate checkInTo
) {
}