package com.voyage.app.booking;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationRepository;
import com.voyage.app.payment.PaymentRepository;
import com.voyage.app.token.RefreshTokenRepository;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceTransactionTest {

    @Autowired BookingService bookingService;
    @Autowired HotelRepository hotelRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoomInventoryRepository roomInventoryRepository;
    @Autowired InventoryService inventoryService;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired NotificationRepository notificationRepository;
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

        hotel = hotelRepository.save(new Hotel("Transaction Hotel", "Paris", 180.0));
        user = userRepository.save(new User("booking-user", "booking-user@test.com", "encoded", Role.CUSTOMER));
        checkIn = LocalDate.now().plusDays(2);
        checkOut = checkIn.plusDays(2);

        inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn, 1);
        inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn.plusDays(1), 1);
    }

    @Test
    void createBooking_commitsReservationAndPayment() {
        Booking booking = bookingService.createBooking(
                user.getUsername(),
                new CreateBookingRequest(hotel.getId(), RoomType.DOUBLE, checkIn, checkOut, "approve")
        );

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(1, bookingRepository.count());
        assertEquals(1, paymentRepository.count());
        assertEquals(0, roomInventoryRepository.findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn, RoomType.DOUBLE).orElseThrow().getAvailableRooms());
        assertEquals(0, roomInventoryRepository.findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn.plusDays(1), RoomType.DOUBLE).orElseThrow().getAvailableRooms());
    }

    @Test
    void createBooking_whenPaymentFails_rollsBackInventoryAndBooking() {
        assertThrows(RuntimeException.class, () -> bookingService.createBooking(
                user.getUsername(),
                new CreateBookingRequest(hotel.getId(), RoomType.DOUBLE, checkIn, checkOut, "decline")
        ));

        assertEquals(0, bookingRepository.count());
        assertEquals(0, paymentRepository.count());
        assertEquals(1, roomInventoryRepository.findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn, RoomType.DOUBLE).orElseThrow().getAvailableRooms());
        assertEquals(1, roomInventoryRepository.findByHotelIdAndDateAndRoomType(hotel.getId(), checkIn.plusDays(1), RoomType.DOUBLE).orElseThrow().getAvailableRooms());
    }
}