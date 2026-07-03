package com.venvify.venvifycore.event.repository;

import com.venvify.venvifycore.event.dto.EventSearchQuery;

import java.time.Instant;
import java.util.List;

/**
 * Fragment tìm kiếm discovery (plan P3 §2.1–2.2) — native SQL vì FULLTEXT
 * {@code MATCH ... AGAINST} không có trong JPQL. Chỉ trả IDs; caller load entity
 * bằng {@code findWithHostByIdIn} (2-query pattern, tránh N+1 host).
 */
public interface EventSearchRepository {

    /** @param from đã normalize non-null ở service (mặc định = now). */
    IdPage searchIds(EventSearchQuery query, Instant from);

    /** Trang IDs theo đúng thứ tự sort + tổng số khớp filter. */
    record IdPage(List<Long> ids, long total) {
    }
}
