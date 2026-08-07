package com.voyage.app.booking;

import com.voyage.app.exception.ForbiddenException;
import com.voyage.app.exception.PaymentFailedException;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.payment.Payment;
import com.voyage.app.payment.PaymentService;
import com.voyage.app.security.HotelAccessService;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingCriteriaRepository bookingCriteriaRepository;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final HotelAccessService hotelAccessService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          BookingCriteriaRepository bookingCriteriaRepository,
                          HotelRepository hotelRepository,
                          UserRepository userRepository,
                          InventoryService inventoryService,
                          PaymentService paymentService,
                          HotelAccessService hotelAccessService,
                          ApplicationEventPublisher applicationEventPublisher) {
        this.bookingRepository = bookingRepository;
        this.bookingCriteriaRepository = bookingCriteriaRepository;
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.hotelAccessService = hotelAccessService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = PaymentFailedException.class)
    public Booking createBooking(String username, CreateBookingRequest request) {
        if (!request.checkOut().isAfter(request.checkIn())) {
            throw new IllegalArgumentException("Check-out must be after check-in");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + request.hotelId()));
        RatePlan ratePlan = request.ratePlanOrDefault();

        for (LocalDate stayDate = request.checkIn(); stayDate.isBefore(request.checkOut()); stayDate = stayDate.plusDays(1)) {
            inventoryService.reserveRoom(hotel.getId(), request.roomType(), stayDate);
        }

        Booking booking = new Booking(
                user,
                hotel,
                request.roomType(),
                request.checkIn(),
                request.checkOut(),
                BookingStatus.PENDING,
                ratePlan,
                calculateTotalPrice(hotel, request.checkIn(), request.checkOut(), ratePlan)
        );
        Booking persistedBooking = bookingRepository.save(booking);

        Payment payment = paymentService.charge(persistedBooking, request.paymentToken());
        if (payment == null) {
            throw new PaymentFailedException("Payment could not be completed");
        }

        persistedBooking.setStatus(BookingStatus.CONFIRMED);
        applicationEventPublisher.publishEvent(new BookingConfirmedEvent(
                persistedBooking.getId(),
                user.getId(),
                user.getUsername(),
                hotel.getId(),
                hotel.getName(),
                persistedBooking.getRoomType(),
                persistedBooking.getCheckIn(),
                persistedBooking.getCheckOut()
        ));
        return persistedBooking;
    }

    @Transactional(readOnly = true)
    public Booking getById(Long bookingId, String username) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (!hotelAccessService.canViewBooking(actor, booking)) {
            throw new ResourceNotFoundException("Booking not found: " + bookingId);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public Long findUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public BookingStatus inspectStatus(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .map(Booking::getStatus)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Booking cancelBooking(Long bookingId, String username) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        boolean owner = booking.getUser().getId().equals(actor.getId());
        boolean managerOfHotel = hotelAccessService.canManageHotel(actor, booking.getHotel());
        boolean admin = hotelAccessService.isAdmin(actor);
        if (!admin && !owner && !managerOfHotel) {
            throw new ResourceNotFoundException("Booking not found: " + bookingId);
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }

        for (LocalDate stayDate = booking.getCheckIn(); stayDate.isBefore(booking.getCheckOut()); stayDate = stayDate.plusDays(1)) {
            inventoryService.releaseRoom(booking.getHotel().getId(), booking.getRoomType(), stayDate);
        }

        booking.setStatus(BookingStatus.CANCELLED);

        // FLEXIBLE bookings auto-refund on cancel; NON_REFUNDABLE keeps the charge
        if (booking.getRatePlan() != null && booking.getRatePlan().isRefundable()) {
            paymentService.refundIfSucceeded(booking.getId());
        }

        applicationEventPublisher.publishEvent(new BookingCancelledEvent(
                booking.getId(),
                booking.getUser().getId(),
                booking.getUser().getUsername(),
                booking.getHotel().getId(),
                booking.getHotel().getName(),
                booking.getRoomType(),
                booking.getCheckIn(),
                booking.getCheckOut()
        ));
        return booking;
    }

    @Transactional(readOnly = true)
    public Page<Booking> searchWithSpecifications(BookingSearchCriteria criteria, Pageable pageable) {
        Specification<Booking> specification = Specification.where(BookingSpec.forUser(criteria.userId()))
                .and(BookingSpec.forHotel(criteria.hotelId()))
                .and(BookingSpec.forHotels(criteria.hotelIds()))
                .and(BookingSpec.hasStatus(criteria.status()))
                .and(BookingSpec.checkInAfter(criteria.checkInFrom()))
                .and(BookingSpec.checkInBefore(criteria.checkInTo()));
        return bookingRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public List<Booking> searchWithCriteria(BookingSearchCriteria criteria) {
        return bookingCriteriaRepository.search(criteria);
    }

    @Transactional(readOnly = true)
    public List<Booking> findByUsername(String username) {
        return bookingRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<Booking> findByHotelAndStatus(Long hotelId, BookingStatus status) {
        return bookingRepository.findByHotelAndStatus(hotelId, status);
    }

    @Transactional(readOnly = true)
    public long countByHotelAndDateRange(Long hotelId, LocalDate from, LocalDate to) {
        return bookingRepository.countByHotelAndDateRange(hotelId, from, to);
    }

    @Transactional(readOnly = true)
    public BookingSearchCriteria resolveSearchCriteria(String username,
                                                       Long userId,
                                                       Long hotelId,
                                                       BookingStatus status,
                                                       LocalDate checkInFrom,
                                                       LocalDate checkInTo) {
        User actor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (hotelAccessService.isAdmin(actor)) {
            return new BookingSearchCriteria(userId, hotelId, null, status, checkInFrom, checkInTo);
        }
        if (hotelAccessService.isHotelManager(actor)) {
            List<Long> managed = hotelAccessService.managedHotelIds(actor);
            if (managed.isEmpty()) {
                return new BookingSearchCriteria(null, -1L, null, status, checkInFrom, checkInTo);
            }
            if (hotelId != null) {
                if (!managed.contains(hotelId)) {
                    throw new ForbiddenException("Not allowed to view bookings for hotel " + hotelId);
                }
                return new BookingSearchCriteria(null, hotelId, null, status, checkInFrom, checkInTo);
            }
            return new BookingSearchCriteria(null, null, managed, status, checkInFrom, checkInTo);
        }
        Long ownUserId = actor.getId();
        return new BookingSearchCriteria(ownUserId, hotelId, null, status, checkInFrom, checkInTo);
    }

    private BigDecimal calculateTotalPrice(Hotel hotel, LocalDate checkIn, LocalDate checkOut, RatePlan ratePlan) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        BigDecimal base = BigDecimal.valueOf(hotel.getPricePerNight()).multiply(BigDecimal.valueOf(nights));
        return base.multiply(ratePlan.getPriceMultiplier()).setScale(2, RoundingMode.HALF_UP);
    }
}
