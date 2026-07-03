package com.venvify.venvifycore.event.dto;

import com.venvify.venvifycore.event.enums.EventCategory;

/** Một dòng cho trang chủ/filter: category + số event PUBLISHED sắp diễn ra (plan P3 §2.3). */
public record CategoryCountResponse(
        EventCategory category,
        long count
) {
}
