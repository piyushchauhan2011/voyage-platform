package com.voyage.app.hotel;

import jakarta.validation.constraints.NotNull;

public record UpdateHotelManagementRequest(
        Long managerId,
        @NotNull SaasPlan saasPlan
) {
}
