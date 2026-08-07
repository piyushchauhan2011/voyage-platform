package com.voyage.app.booking;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository
    extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

  @Query(
      """
            select booking
            from Booking booking
            join booking.user bookingUser
            where bookingUser.username = :username
            order by booking.checkIn asc
            """)
  List<Booking> findByUsername(@Param("username") String username);

  @EntityGraph(attributePaths = {"hotel", "user"})
  @Query(
      """
            select booking
            from Booking booking
            where booking.hotel.id = :hotelId
              and booking.status = :status
            order by booking.checkIn asc
            """)
  List<Booking> findByHotelAndStatus(
      @Param("hotelId") Long hotelId, @Param("status") BookingStatus status);

  @Query(
      """
            select count(booking)
            from Booking booking
            where booking.hotel.id = :hotelId
              and booking.checkIn < :to
              and booking.checkOut > :from
            """)
  long countByHotelAndDateRange(
      @Param("hotelId") Long hotelId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
