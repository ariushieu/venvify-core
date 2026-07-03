package com.venvify.venvifycore.social.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Review event đã tham dự (plan P6 §2): rating 1–5, comment tối đa 2000 ký tự. */
public record CreateReviewRequest(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Short rating,

        @Size(max = 2000, message = "Comment must be at most 2000 characters")
        String comment
) {
}
