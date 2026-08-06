package com.voyage.app.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}
