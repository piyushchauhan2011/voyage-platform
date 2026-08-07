package com.voyage.app.booking;

import com.voyage.app.inventory.RoomType;
import java.time.LocalDate;

public record BookingConfirmedEvent(
    Long bookingId,
    Long userId,
    String username,
    Long hotelId,
    String hotelName,
    RoomType roomType,
    LocalDate checkIn,
    LocalDate checkOut) {}
