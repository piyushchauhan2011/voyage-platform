package com.voyage.app.kafka;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterHotelEventRepository extends JpaRepository<DeadLetterHotelEvent, Long> {

  List<DeadLetterHotelEvent> findTop20ByOrderByDeadLetteredAtDesc();

  Optional<DeadLetterHotelEvent> findById(Long id);

  List<DeadLetterHotelEvent> findByOriginalEventIdOrderByDeadLetteredAtDesc(String originalEventId);
}
