package com.voyage.app.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterHotelEventRepository extends JpaRepository<DeadLetterHotelEvent, Long> {

    List<DeadLetterHotelEvent> findTop20ByOrderByDeadLetteredAtDesc();
}