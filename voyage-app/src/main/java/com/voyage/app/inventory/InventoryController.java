package com.voyage.app.inventory;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

  private final InventoryService inventoryService;

  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @GetMapping
  public List<InventoryResponse> getInventory(
      @RequestParam Long hotelId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) RoomType roomType) {
    return inventoryService.findInventoryResponses(hotelId, from, to, roomType);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InventoryResponse createInventory(@Valid @RequestBody CreateInventoryRequest request) {
    return inventoryService.createInventoryResponse(
        request.hotelId(), request.roomType(), request.date(), request.availableRooms());
  }

  @PutMapping("/{inventoryId}")
  public InventoryResponse updateInventory(
      @PathVariable Long inventoryId, @Valid @RequestBody UpdateInventoryRequest request) {
    return inventoryService.updateAvailabilityResponse(inventoryId, request.availableRooms());
  }
}
