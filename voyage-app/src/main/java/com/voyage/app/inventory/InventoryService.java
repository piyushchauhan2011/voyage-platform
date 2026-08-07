package com.voyage.app.inventory;

import com.voyage.app.exception.BookingNotAvailableException;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.security.HotelAccessService;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class InventoryService {

    private final RoomInventoryRepository roomInventoryRepository;
    private final HotelRepository hotelRepository;
    private final HotelAccessService hotelAccessService;

    public InventoryService(RoomInventoryRepository roomInventoryRepository,
                            HotelRepository hotelRepository,
                            HotelAccessService hotelAccessService) {
        this.roomInventoryRepository = roomInventoryRepository;
        this.hotelRepository = hotelRepository;
        this.hotelAccessService = hotelAccessService;
    }

    @Transactional
    public RoomInventory createInventory(Long hotelId, RoomType roomType, LocalDate date, int availableRooms) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));
        return roomInventoryRepository.save(new RoomInventory(hotel, roomType, date, availableRooms));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> findInventoryResponses(Long hotelId, LocalDate from, LocalDate to, RoomType roomType) {
        return roomInventoryRepository.findInventoryWindow(hotelId, from, to, roomType).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InventoryResponse createInventoryResponse(Long hotelId, RoomType roomType, LocalDate date, int availableRooms) {
        hotelAccessService.assertCanWriteInventory(hotelId);
        return toResponse(createInventory(hotelId, roomType, date, availableRooms));
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
            maxAttemptsExpression = "${application.booking.retry.attempts:3}",
            backoff = @Backoff(delayExpression = "${application.booking.retry.backoff-ms:50}")
    )
    @Transactional
    public void reserveRoom(Long hotelId, RoomType roomType, LocalDate stayDate) {
        adjustInventory(hotelId, roomType, stayDate, -1);
    }

    @Retryable(
            retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
            maxAttemptsExpression = "${application.booking.retry.attempts:3}",
            backoff = @Backoff(delayExpression = "${application.booking.retry.backoff-ms:50}")
    )
    @Transactional
    public void releaseRoom(Long hotelId, RoomType roomType, LocalDate stayDate) {
        adjustInventory(hotelId, roomType, stayDate, 1);
    }

    private void adjustInventory(Long hotelId, RoomType roomType, LocalDate stayDate, int delta) {
        RoomInventory inventory = roomInventoryRepository.findForUpdate(hotelId, stayDate, roomType)
                .orElseThrow(() -> new BookingNotAvailableException("Inventory not configured for %s on %s".formatted(roomType, stayDate)));
        if (delta < 0 && inventory.getAvailableRooms() <= 0) {
            throw new BookingNotAvailableException("No rooms available for %s on %s".formatted(roomType, stayDate));
        }
        inventory.setAvailableRooms(inventory.getAvailableRooms() + delta);
    }

    @Transactional
    public RoomInventory updateAvailability(Long inventoryId, int availableRooms) {
        RoomInventory inventory = roomInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + inventoryId));
        inventory.setAvailableRooms(availableRooms);
        return roomInventoryRepository.save(inventory);
    }

    @Transactional
    public InventoryResponse updateAvailabilityResponse(Long inventoryId, int availableRooms) {
        RoomInventory inventory = roomInventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + inventoryId));
        hotelAccessService.assertCanWriteInventory(inventory.getHotel().getId());
        inventory.setAvailableRooms(availableRooms);
        return toResponse(roomInventoryRepository.save(inventory));
    }

    private InventoryResponse toResponse(RoomInventory inventory) {
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