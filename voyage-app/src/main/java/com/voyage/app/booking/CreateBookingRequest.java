package com.voyage.app.booking;

import com.voyage.app.inventory.RoomType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long hotelId,
        @NotNull RoomType roomType,
        @NotNull @Future LocalDate checkIn,
        @NotNull @Future LocalDate checkOut,
        @NotBlank String paymentToken
) {
}