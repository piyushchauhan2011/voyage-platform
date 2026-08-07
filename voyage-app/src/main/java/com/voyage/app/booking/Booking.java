package com.voyage.app.booking;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Booking(User user,
                   Hotel hotel,
                   RoomType roomType,
                   LocalDate checkIn,
                   LocalDate checkOut,
                   BookingStatus status,
                   BigDecimal totalPrice) {
        this.user = user;
        this.hotel = hotel;
        this.roomType = roomType;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
        this.totalPrice = totalPrice;
    }

    @PrePersist
    void markCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}