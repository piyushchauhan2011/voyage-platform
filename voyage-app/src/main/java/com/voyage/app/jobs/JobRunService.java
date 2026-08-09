package com.voyage.app.jobs;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobRunService {

  private final JobRunRepository jobRunRepository;
  private final Clock clock;

  public JobRunService(JobRunRepository jobRunRepository, Clock clock) {
    this.jobRunRepository = jobRunRepository;
    this.clock = clock;
  }

  @Transactional
  public JobRun record(
      String jobId,
      String type,
      JobSource source,
      JobRunStatus status,
      String payload,
      String error) {
    String snippet = payload == null ? "" : payload;
    if (snippet.length() > 500) {
      snippet = snippet.substring(0, 500);
    }
    return jobRunRepository.save(
        new JobRun(jobId, type, source, status, snippet, error, Instant.now(clock)));
  }

  @Transactional(readOnly = true)
  public List<JobRun> recent() {
    return jobRunRepository.findTop50ByOrderByCreatedAtDesc();
  }

  @Transactional
  public int deleteOlderThan(Instant cutoff) {
    return jobRunRepository.deleteByCreatedAtBefore(cutoff);
  }
}
