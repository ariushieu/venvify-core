package com.venvify.venvifycore.booking.dto;

import com.venvify.venvifycore.booking.enums.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        String publicId,
        String eventPublicId,
        String eventTitle,
        BookingStatus status,
        Long pricePaid,
        Instant bookedAt
) {
}
