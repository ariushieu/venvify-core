package com.venvify.venvifycore.social.dto;

import java.time.Instant;

/** Review public (đã lọc hidden). {@code eventTitle} chỉ set ở listing theo host. */
public record ReviewResponse(
        String publicId,
        short rating,
        String comment,
        String reviewerName,
        String reviewerAvatarUrl,
        String eventTitle,
        Instant createdAt
) {
}
