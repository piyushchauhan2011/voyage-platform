package com.voyage.app.security;

import com.voyage.app.booking.Booking;
import com.voyage.app.booking.RatePlan;
import com.voyage.app.exception.ForbiddenException;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.hotel.SaasPlan;
import com.voyage.app.payment.Payment;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Attribute-based access checks for hotels, inventory, bookings, and payments.
 * Roles are coarse gates; this service evaluates resource attributes (ownership, SaaS plan, rate plan).
 */
@Service
public class HotelAccessService {

    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;

    public HotelAccessService(HotelRepository hotelRepository, UserRepository userRepository) {
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Authentication required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public boolean isHotelManager(User user) {
        return user.getRole() == Role.HOTEL_MANAGER;
    }

    public boolean isCustomer(User user) {
        return user.getRole() == Role.CUSTOMER;
    }

    @Transactional(readOnly = true)
    public Hotel requireHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));
    }

    @Transactional(readOnly = true)
    public boolean canManageHotel(User user, Hotel hotel) {
        if (isAdmin(user)) {
            return true;
        }
        return isHotelManager(user)
                && hotel.getManager() != null
                && hotel.getManager().getId().equals(user.getId());
    }

    @Transactional(readOnly = true)
    public void assertCanManageHotel(Long hotelId) {
        User user = requireCurrentUser();
        Hotel hotel = requireHotel(hotelId);
        if (!canManageHotel(user, hotel)) {
            throw new ForbiddenException("Not allowed to manage hotel " + hotelId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanWriteInventory(Long hotelId) {
        User user = requireCurrentUser();
        Hotel hotel = requireHotel(hotelId);
        if (!canManageHotel(user, hotel)) {
            throw new ForbiddenException("Not allowed to manage hotel " + hotelId);
        }
        if (!isAdmin(user) && !hotel.getSaasPlan().isInventoryWritesAllowed()) {
            throw new ForbiddenException(
                    "Inventory writes require PRO or ENTERPRISE plan (hotel plan is " + hotel.getSaasPlan() + ")");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateHotel(User manager) {
        if (isAdmin(manager)) {
            return;
        }
        if (!isHotelManager(manager)) {
            throw new ForbiddenException("Only ADMIN or HOTEL_MANAGER can create hotels");
        }
        SaasPlan effectivePlan = effectivePlanForManager(manager.getId());
        long owned = hotelRepository.countByManager_Id(manager.getId());
        if (owned >= effectivePlan.getMaxHotels()) {
            throw new ForbiddenException(
                    "Hotel limit reached for plan " + effectivePlan + " (max " + effectivePlan.getMaxHotels() + ")");
        }
    }

    @Transactional(readOnly = true)
    public SaasPlan effectivePlanForManager(Long managerId) {
        return hotelRepository.findByManager_Id(managerId).stream()
                .map(Hotel::getSaasPlan)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(SaasPlan.FREE);
    }

    @Transactional(readOnly = true)
    public List<Long> managedHotelIds(User user) {
        return hotelRepository.findByManager_Id(user.getId()).stream()
                .map(Hotel::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean canViewBooking(User user, Booking booking) {
        if (isAdmin(user)) {
            return true;
        }
        if (booking.getUser().getId().equals(user.getId())) {
            return true;
        }
        return canManageHotel(user, booking.getHotel());
    }

    @Transactional(readOnly = true)
    public boolean canViewPayment(User user, Payment payment) {
        return canViewBooking(user, payment.getBooking());
    }

    @Transactional(readOnly = true)
    public boolean canRefund(User user, Payment payment) {
        if (isAdmin(user)) {
            return true;
        }
        Booking booking = payment.getBooking();
        if (isCustomer(user) && booking.getUser().getId().equals(user.getId())) {
            return booking.getRatePlan() != null && booking.getRatePlan().isRefundable();
        }
        if (canManageHotel(user, booking.getHotel())) {
            return booking.getHotel().getSaasPlan().isRefundsAllowed();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public void assertCanRefund(Payment payment) {
        User user = requireCurrentUser();
        if (!canRefund(user, payment)) {
            RatePlan ratePlan = payment.getBooking().getRatePlan();
            SaasPlan saasPlan = payment.getBooking().getHotel().getSaasPlan();
            throw new ForbiddenException(
                    "Refund not allowed (role=" + user.getRole()
                            + ", ratePlan=" + ratePlan
                            + ", hotelSaasPlan=" + saasPlan + ")");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanViewPayment(Payment payment) {
        User user = requireCurrentUser();
        if (!canViewPayment(user, payment)) {
            throw new ForbiddenException("Not allowed to view this payment");
        }
    }
}
