package com.voyage.app.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationRepository;
import com.voyage.app.payment.PaymentRepository;
import com.voyage.app.token.RefreshTokenRepository;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BookingSearchIntegrationTest {

  @Autowired BookingService bookingService;
  @Autowired BookingRepository bookingRepository;
  @Autowired HotelRepository hotelRepository;
  @Autowired UserRepository userRepository;
  @Autowired NotificationRepository notificationRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;

  private User alice;
  private User bob;
  private Hotel tokyo;
  private Hotel paris;

  @BeforeEach
  void setUp() {
    notificationRepository.deleteAll();
    paymentRepository.deleteAll();
    bookingRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    userRepository.deleteAll();
    hotelRepository.deleteAll();

    alice =
        userRepository.save(
            new User("alice-bookings", "alice-bookings@test.com", "encoded", Role.CUSTOMER));
    bob =
        userRepository.save(
            new User("bob-bookings", "bob-bookings@test.com", "encoded", Role.CUSTOMER));
    tokyo = hotelRepository.save(new Hotel("Tokyo Grand", "Tokyo", 220.0));
    paris = hotelRepository.save(new Hotel("Paris Central", "Paris", 180.0));

    bookingRepository.save(
        new Booking(
            alice,
            tokyo,
            RoomType.SUITE,
            LocalDate.now().plusDays(5),
            LocalDate.now().plusDays(8),
            BookingStatus.CONFIRMED,
            BigDecimal.valueOf(660)));
    bookingRepository.save(
        new Booking(
            alice,
            paris,
            RoomType.DOUBLE,
            LocalDate.now().plusDays(10),
            LocalDate.now().plusDays(12),
            BookingStatus.CANCELLED,
            BigDecimal.valueOf(360)));
    bookingRepository.save(
        new Booking(
            bob,
            tokyo,
            RoomType.SINGLE,
            LocalDate.now().plusDays(6),
            LocalDate.now().plusDays(7),
            BookingStatus.CONFIRMED,
            BigDecimal.valueOf(220)));
  }

  @Test
  void jpqlQueriesReturnExpectedBookings() {
    List<Booking> aliceBookings = bookingService.findByUsername(alice.getUsername());
    List<Booking> confirmedTokyoBookings =
        bookingService.findByHotelAndStatus(tokyo.getId(), BookingStatus.CONFIRMED);
    long overlapCount =
        bookingService.countByHotelAndDateRange(
            tokyo.getId(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7));

    assertEquals(2, aliceBookings.size());
    assertEquals(2, confirmedTokyoBookings.size());
    assertEquals(2, overlapCount);
  }

  @Test
  void specificationsFilterBookings() {
    BookingSearchCriteria criteria =
        new BookingSearchCriteria(
            alice.getId(),
            null,
            BookingStatus.CONFIRMED,
            LocalDate.now().plusDays(4),
            LocalDate.now().plusDays(6));

    List<Booking> result =
        bookingService.searchWithSpecifications(criteria, PageRequest.of(0, 10)).getContent();

    assertEquals(1, result.size());
    assertEquals(tokyo.getId(), result.getFirst().getHotel().getId());
  }

  @Test
  void criteriaApiFiltersBookings() {
    BookingSearchCriteria criteria =
        new BookingSearchCriteria(
            null,
            paris.getId(),
            BookingStatus.CANCELLED,
            LocalDate.now().plusDays(9),
            LocalDate.now().plusDays(11));

    List<Booking> result = bookingService.searchWithCriteria(criteria);

    assertEquals(1, result.size());
    assertEquals(alice.getId(), result.getFirst().getUser().getId());
  }
}
