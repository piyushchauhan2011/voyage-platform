package com.voyage.app.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessedHotelEventRepository extends JpaRepository<ProcessedHotelEvent, Long> {

    boolean existsByEventId(String eventId);

    List<ProcessedHotelEvent> findTop20ByOrderByProcessedAtDesc();

    long countByHotelIdAndEventType(Long hotelId, HotelEventType eventType);

    Optional<ProcessedHotelEvent> findFirstByHotelIdAndEventTypeOrderByProcessedAtDesc(Long hotelId,
                                                                                        HotelEventType eventType);
}