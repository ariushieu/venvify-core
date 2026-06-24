package com.venvify.venvifycore.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @Size(max = 150)
        String fullName,

        @Size(max = 500)
        String avatarUrl,

        String bio,

        @Size(max = 60)
        String hostHandle
) {
}
