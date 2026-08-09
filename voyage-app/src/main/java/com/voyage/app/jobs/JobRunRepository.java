package com.voyage.app.jobs;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

  List<JobRun> findTop50ByOrderByCreatedAtDesc();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from JobRun j where j.createdAt < :cutoff")
  int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
