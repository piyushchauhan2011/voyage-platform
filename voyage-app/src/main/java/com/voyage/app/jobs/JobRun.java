package com.voyage.app.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "job_runs",
    indexes = {@Index(name = "idx_job_runs_created_at", columnList = "created_at")})
@Getter
@Setter
@NoArgsConstructor
public class JobRun {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id", nullable = false, length = 64)
  private String jobId;

  @Column(nullable = false, length = 64)
  private String type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private JobSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private JobRunStatus status;

  @Column(name = "payload_snippet", nullable = false, length = 512)
  private String payloadSnippet;

  @Column(length = 1024)
  private String error;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  public JobRun(
      String jobId,
      String type,
      JobSource source,
      JobRunStatus status,
      String payloadSnippet,
      String error,
      Instant finishedAt) {
    this.jobId = jobId;
    this.type = type;
    this.source = source;
    this.status = status;
    this.payloadSnippet = payloadSnippet;
    this.error = error;
    this.finishedAt = finishedAt;
  }

  @PrePersist
  void initialize() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
