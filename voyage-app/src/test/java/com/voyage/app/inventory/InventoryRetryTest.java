package com.voyage.app.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class InventoryRetryTest {

  @Autowired InventoryService inventoryService;
  @MockitoBean RoomInventoryRepository roomInventoryRepository;

  @Test
  void reserveRoom_retriesOnLockFailure() {
    RoomInventory inventory = new RoomInventory();
    inventory.setAvailableRooms(2);

    doThrow(new CannotAcquireLockException("deadlock"))
        .doReturn(Optional.of(inventory))
        .when(roomInventoryRepository)
        .findForUpdate(42L, LocalDate.of(2030, 1, 10), RoomType.SUITE);

    assertDoesNotThrow(
        () -> inventoryService.reserveRoom(42L, RoomType.SUITE, LocalDate.of(2030, 1, 10)));
    verify(roomInventoryRepository, times(2))
        .findForUpdate(42L, LocalDate.of(2030, 1, 10), RoomType.SUITE);
  }
}
