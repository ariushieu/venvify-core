package com.venvify.venvifycore.user.dto;

import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Response công khai — KHÔNG chứa id nội bộ hay password_hash (CLAUDE.md §4, §5).
 */
public record UserResponse(
        String publicId,
        String email,
        String fullName,
        String avatarUrl,
        String bio,
        String hostHandle,
        boolean emailVerified,
        UserStatus status,
        Set<Role> roles,
        Instant createdAt
) {
}
