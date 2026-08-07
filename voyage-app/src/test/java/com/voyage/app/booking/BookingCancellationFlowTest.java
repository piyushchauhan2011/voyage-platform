package com.voyage.app.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationRepository;
import com.voyage.app.notification.NotificationType;
import com.voyage.app.payment.PaymentRepository;
import com.voyage.app.token.RefreshTokenRepository;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BookingCancellationFlowTest {

  @Autowired BookingService bookingService;
  @Autowired HotelRepository hotelRepository;
  @Autowired UserRepository userRepository;
  @Autowired InventoryService inventoryService;
  @Autowired RoomInventoryRepository roomInventoryRepository;
  @Autowired NotificationRepository notificationRepository;
  @Autowired BookingRepository bookingRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;

  private Hotel hotel;
  private User user;
  private LocalDate checkIn;
  private LocalDate checkOut;

  @BeforeEach
  void setUp() {
    notificationRepository.deleteAll();
    paymentRepository.deleteAll();
    bookingRepository.deleteAll();
    roomInventoryRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    userRepository.deleteAll();
    hotelRepository.deleteAll();

    hotel = hotelRepository.save(new Hotel("Cancel Hotel", "Lisbon", 190.0));
    user =
        userRepository.save(
            new User("cancel-user", "cancel-user@test.com", "encoded", Role.CUSTOMER));
    checkIn = LocalDate.now().plusDays(3);
    checkOut = checkIn.plusDays(2);

    inventoryService.createInventory(hotel.getId(), RoomType.SUITE, checkIn, 1);
    inventoryService.createInventory(hotel.getId(), RoomType.SUITE, checkIn.plusDays(1), 1);
  }

  @Test
  void cancellingBooking_restoresInventoryAndCreatesCancellationNotification() {
    Booking booking =
        bookingService.createBooking(
            user.getUsername(),
            new CreateBookingRequest(hotel.getId(), RoomType.SUITE, checkIn, checkOut, "approve"));

    Booking cancelled = bookingService.cancelBooking(booking.getId(), user.getUsername());

    assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
    assertEquals(
        1,
        roomInventoryRepository
            .findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn, RoomType.SUITE)
            .orElseThrow()
            .getAvailableRooms());
    assertEquals(
        1,
        roomInventoryRepository
            .findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn.plusDays(1), RoomType.SUITE)
            .orElseThrow()
            .getAvailableRooms());
    assertEquals(2, notificationRepository.count());
    assertEquals(
        NotificationType.BOOKING_CANCELLED,
        notificationRepository.findAll().stream()
            .map(notification -> notification.getType())
            .max(Enum::compareTo)
            .orElseThrow());
  }
}
