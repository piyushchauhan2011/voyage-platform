package com.voyage.app.inventory;

import java.time.LocalDate;

public record InventoryResponse(
        Long id,
        Long hotelId,
        String hotelName,
        RoomType roomType,
        LocalDate date,
        int availableRooms
) {
    public static InventoryResponse from(RoomInventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getHotel().getId(),
                inventory.getHotel().getName(),
                inventory.getRoomType(),
                inventory.getDate(),
                inventory.getAvailableRooms()
        );
    }
}