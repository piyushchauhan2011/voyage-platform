package com.voyage.app.inventory;

import com.voyage.app.hotel.Hotel;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "room_inventory",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_room_inventory_hotel_date_type",
          columnNames = {"hotel_id", "inventory_date", "room_type"})
    })
@Getter
@Setter
@NoArgsConstructor
public class RoomInventory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hotel_id", nullable = false)
  private Hotel hotel;

  @Enumerated(EnumType.STRING)
  @Column(name = "room_type", nullable = false)
  @NotNull
  private RoomType roomType;

  @Column(name = "inventory_date", nullable = false)
  @NotNull
  private LocalDate date;

  @Column(name = "available_rooms", nullable = false)
  @Min(0)
  private int availableRooms;

  public RoomInventory(Hotel hotel, RoomType roomType, LocalDate date, int availableRooms) {
    this.hotel = hotel;
    this.roomType = roomType;
    this.date = date;
    this.availableRooms = availableRooms;
  }

  @PrePersist
  void validateAvailability() {
    if (availableRooms < 0) {
      throw new IllegalStateException("Inventory cannot be negative");
    }
  }
}
