package com.voyage.app.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {
}