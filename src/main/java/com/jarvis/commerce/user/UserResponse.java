package com.jarvis.commerce.user;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String phone,
        UserStatus status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getStatus(), user.getVersion(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
