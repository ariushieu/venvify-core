package com.venvify.venvifycore.admin.dto;

import jakarta.validation.constraints.Size;

/** Body chung cho mutation admin cần lý do (ban/takedown/hide). */
public record ModerationRequest(
        @Size(max = 1000, message = "Reason must be at most 1000 characters")
        String reason
) {
}
