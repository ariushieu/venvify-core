package com.venvify.venvifycore.social.dto;

import java.time.Instant;

/** Một dòng trong danh sách host tôi đang follow (plan P6 §1). */
public record FollowedHostResponse(
        String handle,
        String name,
        String avatarUrl,
        Instant followedAt
) {
}
