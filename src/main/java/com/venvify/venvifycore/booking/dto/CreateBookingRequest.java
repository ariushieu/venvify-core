package com.venvify.venvifycore.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBookingRequest(

        @NotBlank(message = "Event id is required")
        String eventPublicId
) {
}
