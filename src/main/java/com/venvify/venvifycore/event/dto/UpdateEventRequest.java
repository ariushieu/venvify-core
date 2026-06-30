package com.venvify.venvifycore.event.dto;

import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventTimezone;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateEventRequest(

        @Size(max = 200)
        String title,

        String description,

        EventCategory category,

        Instant startTime,

        Instant endTime,

        EventTimezone timezone,

        @Min(1)
        Integer maxSlots,

        @PositiveOrZero
        Long priceAmount,

        @Size(max = 500)
        String coverImageUrl
) {
}
