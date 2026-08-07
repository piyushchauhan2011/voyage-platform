package com.voyage.app.kafka;

public enum DeadLetterRetryStatus {
  PENDING,
  RETRIED,
  RESOLVED,
  FAILED_AGAIN
}
