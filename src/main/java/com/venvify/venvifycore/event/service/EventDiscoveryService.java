package com.venvify.venvifycore.event.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.event.dto.CategoryCountResponse;
import com.venvify.venvifycore.event.dto.EventCardResponse;
import com.venvify.venvifycore.event.dto.EventSearchQuery;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.mapper.EventMapper;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.event.repository.EventSearchRepository.IdPage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovery/search công khai (plan P3 §2) — tách khỏi {@link EventService} (vòng đời event)
 * để mỗi service một trách nhiệm. Cache Caffeine 60s cho đường KHÔNG có q (search bypass);
 * key = toString() của record query (from=null giữ nguyên trong key để hit ổn định trong TTL).
 */
@Service
@RequiredArgsConstructor
public class EventDiscoveryService {

    static final int MAX_PAGE_SIZE = 100;

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    @Cacheable(cacheNames = "discover", condition = "!#query.hasTextSearch()", key = "#query.toString()")
    @Transactional(readOnly = true)
    public PagedResponse<EventCardResponse> search(EventSearchQuery query) {
        validate(query);

        // from mặc định = now chốt về đầu phút: giá trị cache (TTL 60s) và query khớp nhau,
        // không tạo key mới mỗi request. Sai lệch tối đa 1 phút — chấp nhận (plan §2.3).
        Instant from = query.from() != null
                ? query.from()
                : Instant.now().truncatedTo(ChronoUnit.MINUTES);

        IdPage idPage = eventRepository.searchIds(query, from);
        List<EventCardResponse> items = loadCardsInOrder(idPage.ids());

        int totalPages = (int) Math.ceil((double) idPage.total() / query.size());
        boolean last = query.page() >= totalPages - 1;
        return new PagedResponse<>(items, query.page(), query.size(), idPage.total(), totalPages, last);
    }

    /** Đủ 11 category kể cả count 0 — FE render filter không phải tự vá lỗ. */
    @Cacheable(cacheNames = "categories")
    @Transactional(readOnly = true)
    public List<CategoryCountResponse> countByCategory() {
        Map<EventCategory, Long> counts = new EnumMap<>(EventCategory.class);
        eventRepository.countUpcomingByCategory(EventStatus.PUBLISHED, Instant.now())
                .forEach(row -> counts.put(row.getCategory(), row.getTotal()));

        List<CategoryCountResponse> result = new ArrayList<>(EventCategory.values().length);
        for (EventCategory category : EventCategory.values()) {
            result.add(new CategoryCountResponse(category, counts.getOrDefault(category, 0L)));
        }
        return result;
    }

    // ----- helpers -----

    private static void validate(EventSearchQuery query) {
        if (query.page() < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BadRequestException("'from' must be before 'to'");
        }
    }

    /** IN (:ids) không giữ thứ tự — xếp lại theo trang IDs đã sort từ bước 1. */
    private List<EventCardResponse> loadCardsInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Event> byId = eventRepository.findWithHostByIdIn(ids).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .map(eventMapper::toCard)
                .toList();
    }
}
