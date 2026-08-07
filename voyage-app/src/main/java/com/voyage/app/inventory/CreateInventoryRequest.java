package com.voyage.app.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateInventoryRequest(
        @NotNull Long hotelId,
        @NotNull RoomType roomType,
        @NotNull LocalDate date,
        @Min(0) int availableRooms
) {
}