package com.venvify.venvifycore.event.dto;

import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.enums.EventTimezone;

import java.time.Instant;

public record EventResponse(
        String publicId,
        String hostPublicId,
        String hostHandle,
        String title,
        String slug,
        String description,
        EventCategory category,
        Instant startTime,
        Instant endTime,
        EventTimezone timezone,
        Integer maxSlots,
        Integer claimedSlots,
        Long priceAmount,
        EventStatus status,
        String coverImageUrl,
        Instant createdAt
) {
}
