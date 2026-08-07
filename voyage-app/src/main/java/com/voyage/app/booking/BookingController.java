package com.voyage.app.booking;

import com.voyage.app.common.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

  private final BookingService bookingService;

  public BookingController(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookingResponse createBooking(
      Authentication authentication, @Valid @RequestBody CreateBookingRequest request) {
    return BookingResponse.from(bookingService.createBooking(authentication.getName(), request));
  }

  @GetMapping
  public PageResponse<BookingResponse> getBookings(
      Authentication authentication,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long hotelId,
      @RequestParam(required = false) BookingStatus status,
      @RequestParam(required = false) LocalDate checkInFrom,
      @RequestParam(required = false) LocalDate checkInTo,
      @PageableDefault(size = 20, sort = "checkIn") Pageable pageable) {
    BookingSearchCriteria criteria =
        bookingService.resolveSearchCriteria(
            authentication.getName(), userId, hotelId, status, checkInFrom, checkInTo);
    return PageResponse.from(
        bookingService.searchWithSpecifications(criteria, pageable).map(BookingResponse::from));
  }

  @GetMapping("/{bookingId}")
  public BookingResponse getBooking(Authentication authentication, @PathVariable Long bookingId) {
    return BookingResponse.from(bookingService.getById(bookingId, authentication.getName()));
  }

  @DeleteMapping("/{bookingId}")
  public BookingResponse cancelBooking(
      Authentication authentication, @PathVariable Long bookingId) {
    return BookingResponse.from(bookingService.cancelBooking(bookingId, authentication.getName()));
  }
}
