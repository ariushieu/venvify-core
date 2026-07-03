package com.venvify.venvifycore.event.dto;

import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventListSort;
import com.venvify.venvifycore.event.enums.PriceType;

import java.time.Instant;

/**
 * Tham số discovery THÔ từ controller (plan P3 §2.1) — chưa normalize, service tự xử.
 * Record để {@code toString()} ổn định làm cache key (Caffeine, chỉ khi không có {@code q});
 * {@code from} null = "sắp diễn ra" default ở service (không chốt now ở đây để key ổn định trong TTL).
 */
public record EventSearchQuery(
        String q,
        EventCategory category,
        PriceType priceType,
        Instant from,
        Instant to,
        EventListSort sort,
        int page,
        int size
) {

    public boolean hasTextSearch() {
        return q != null && !q.isBlank();
    }
}
