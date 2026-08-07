package com.voyage.app.user;

public record UserProfileResponse(Long id, String username, String email, Role role) {
}