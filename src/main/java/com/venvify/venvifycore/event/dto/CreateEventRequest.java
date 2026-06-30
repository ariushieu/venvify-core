package com.venvify.venvifycore.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Tạo event ở trạng thái DRAFT (plan §1 E3). Thời gian + timezone để NULL khi nháp;
 * service bắt buộc đủ {@code startTime/endTime/timezone} khi PUBLISH, không validate ở đây.
 */
public record CreateEventRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        String description,

        @Size(max = 50)
        String category,

        /** Có thể NULL khi DRAFT; bắt buộc khi publish. */
        Instant startTime,

        /** Có thể NULL khi DRAFT; bắt buộc khi publish. */
        Instant endTime,

        /** Múi giờ IANA (vd Asia/Ho_Chi_Minh). NULL khi DRAFT; bắt buộc khi publish. */
        @Size(max = 40)
        String timezone,

        @NotNull
        @Min(value = 1, message = "Minimum number of slots is 1")
        Integer maxSlots,

        /** VND nguyên, 0 = free. */
        @NotNull
        @PositiveOrZero(message = "Price must not be negative")
        Long priceAmount
) {
}
