package com.voyage.app.inventory;

import jakarta.validation.constraints.Min;

public record UpdateInventoryRequest(@Min(0) int availableRooms) {
}