package com.venvify.venvifycore.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateEventRequest(

        @Size(max = 200)
        String title,

        String description,

        @Size(max = 50)
        String category,

        Instant startTime,

        Instant endTime,

        @Size(max = 40)
        String timezone,

        @Min(1)
        Integer maxSlots,

        @PositiveOrZero
        Long priceAmount,

        @Size(max = 500)
        String coverImageUrl
) {
}
