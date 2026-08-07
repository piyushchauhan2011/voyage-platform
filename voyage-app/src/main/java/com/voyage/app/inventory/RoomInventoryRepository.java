package com.voyage.app.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long>, JpaSpecificationExecutor<RoomInventory> {

  Optional<RoomInventory> findByHotelIdAndDateAndRoomType(Long hotelId, LocalDate date, RoomType roomType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inventory
            from RoomInventory inventory
            where inventory.hotel.id = :hotelId
              and inventory.date = :date
              and inventory.roomType = :roomType
            """)
    Optional<RoomInventory> findForUpdate(@Param("hotelId") Long hotelId,
                                          @Param("date") LocalDate date,
                                          @Param("roomType") RoomType roomType);

    @Query("""
            select inventory
            from RoomInventory inventory
            where inventory.hotel.id = :hotelId
              and inventory.date between :from and :to
              and (:roomType is null or inventory.roomType = :roomType)
            order by inventory.date asc
            """)
    List<RoomInventory> findInventoryWindow(@Param("hotelId") Long hotelId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("roomType") RoomType roomType);
}