package com.venvify.venvifycore.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateEventRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        String description,

        @Size(max = 50)
        String category,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        @NotNull
        @Min(value = 1, message = "Minimum number of slots is 1")
        Integer maxSlots,

        /** VND nguyên, 0 = free. */
        @NotNull
        @PositiveOrZero(message = "Price must not be negative")
        Long priceAmount
) {
}
