package com.venvify.venvifycore.event.dto;

import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventTimezone;

import java.time.Instant;

/**
 * Card gọn cho trang discover/list (plan P3 §2.1) — nhẹ hơn {@link EventResponse},
 * kèm thông tin host để render không cần gọi thêm (fetch join, không N+1).
 */
public record EventCardResponse(
        String publicId,
        String slug,
        String title,
        String coverImageUrl,
        EventCategory category,
        Instant startTime,
        Instant endTime,
        EventTimezone timezone,
        Long priceAmount,
        Integer slotsLeft,
        String hostHandle,
        String hostName,
        String hostAvatarUrl
) {
}
