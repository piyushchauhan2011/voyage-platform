package com.voyage.app.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError validation(int status, String error, String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, List.copyOf(fieldErrors));
    }

    public record FieldError(String field, String message) {
    }
}