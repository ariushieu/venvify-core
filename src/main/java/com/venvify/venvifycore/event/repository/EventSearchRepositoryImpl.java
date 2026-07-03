package com.venvify.venvifycore.event.repository;

import com.venvify.venvifycore.event.dto.EventSearchQuery;
import com.venvify.venvifycore.event.enums.EventListSort;
import com.venvify.venvifycore.event.enums.PriceType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WHERE build động nhưng giá trị LUÔN đi qua bind parameter — không bao giờ nối chuỗi
 * user input vào SQL. Index dùng: ft_events_title_desc (đường q) · idx_events_status +
 * idx_events_start (filter thuần) — EXPLAIN cả 2 đường trước khi ship (plan §2.2).
 */
public class EventSearchRepositoryImpl implements EventSearchRepository {

    /**
     * innodb_ft_min_token_size mặc định = 3: token ngắn hơn không nằm trong FULLTEXT index.
     * Token < 3 ký tự bị loại khỏi câu boolean (giữ lại sẽ "+ab*" match rỗng → 0 kết quả oan);
     * cả câu q không còn token nào dùng được → fallback LIKE prefix trên title.
     */
    private static final int MIN_FULLTEXT_TOKEN = 3;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public IdPage searchIds(EventSearchQuery query, Instant from) {
        StringBuilder where = new StringBuilder(
                " from events e where e.status = 'PUBLISHED' and e.is_deleted = false");
        Map<String, Object> params = new HashMap<>();

        where.append(" and e.start_time >= :fromTime");
        params.put("fromTime", from);
        if (query.to() != null) {
            where.append(" and e.start_time <= :toTime");
            params.put("toTime", query.to());
        }
        if (query.category() != null) {
            where.append(" and e.category = :category");
            params.put("category", query.category().name());
        }
        if (query.priceType() == PriceType.FREE) {
            where.append(" and e.price_amount = 0");
        } else if (query.priceType() == PriceType.PAID) {
            where.append(" and e.price_amount > 0");
        }
        if (query.hasTextSearch()) {
            String booleanQuery = toBooleanModeQuery(query.q());
            if (booleanQuery != null) {
                where.append(" and match(e.title, e.description) against (:ftQ in boolean mode)");
                params.put("ftQ", booleanQuery);
            } else {
                where.append(" and e.title like :likeQ");
                params.put("likeQ", escapeLike(query.q().trim()) + "%");
            }
        }

        String order = query.sort() == EventListSort.NEWEST
                ? " order by e.id desc"
                : " order by e.start_time asc, e.id asc";

        Query idQuery = entityManager.createNativeQuery("select e.id" + where + order);
        params.forEach(idQuery::setParameter);
        idQuery.setFirstResult(query.page() * query.size());
        idQuery.setMaxResults(query.size());
        @SuppressWarnings("unchecked")
        List<Number> rows = idQuery.getResultList();
        List<Long> ids = new ArrayList<>(rows.size());
        rows.forEach(n -> ids.add(n.longValue()));

        Query countQuery = entityManager.createNativeQuery("select count(*)" + where);
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new IdPage(ids, total);
    }

    /**
     * "lập trình web" → "+lập* +trình* +web*" (AND các từ, prefix match). Ký tự toán tử
     * boolean mode bị lột khỏi token (user không inject cú pháp FULLTEXT được).
     * Trả null nếu không còn token đủ dài → caller đi đường LIKE.
     */
    static String toBooleanModeQuery(String q) { // package-private cho unit test tokenizer
        StringBuilder sb = new StringBuilder();
        for (String raw : q.trim().split("\\s+")) {
            String token = raw.replaceAll("[+\\-<>()~*\"@\\\\]", "");
            if (token.length() < MIN_FULLTEXT_TOKEN) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append('+').append(token).append('*');
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** Escape wildcard LIKE để q là literal prefix ('%'/'_'/'\' của user không thành pattern). */
    static String escapeLike(String s) { // package-private cho unit test
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
