package com.venvify.venvifycore.event.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.event.dto.CategoryCountResponse;
import com.venvify.venvifycore.event.dto.CreateEventRequest;
import com.venvify.venvifycore.event.dto.EventCardResponse;
import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.dto.EventSearchQuery;
import com.venvify.venvifycore.event.dto.UpdateEventRequest;
import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventListSort;
import com.venvify.venvifycore.event.enums.PriceType;
import com.venvify.venvifycore.event.service.EventDiscoveryService;
import com.venvify.venvifycore.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventDiscoveryService eventDiscoveryService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> create(
            @AuthenticationPrincipal String publicId,
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse event = eventService.create(publicId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED.value(), "Event created", event));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<ApiResponse<EventResponse>> update(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId,
            @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.update(userPublicId, publicId, request)));
    }

    @PatchMapping("/{publicId}/publish")
    public ResponseEntity<ApiResponse<EventResponse>> publish(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.publish(userPublicId, publicId), "Event published"));
    }

    @PatchMapping("/{publicId}/cancel")
    public ResponseEntity<ApiResponse<EventResponse>> cancel(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.cancel(userPublicId, publicId), "Event cancelled"));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        eventService.delete(userPublicId, publicId);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Event deleted"));
    }

    /**
     * Công khai: discover/search (plan P3 §2.1). Mặc định PUBLISHED sắp diễn ra, sort upcoming;
     * page/size tường minh (sort đi bằng param riêng, không dùng Pageable sort).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EventCardResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EventCategory category,
            @RequestParam(required = false) PriceType priceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "UPCOMING") EventListSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        EventSearchQuery query = new EventSearchQuery(q, category, priceType, from, to, sort, page, size);
        return ResponseEntity.ok(ApiResponse.ok(eventDiscoveryService.search(query)));
    }

    /** Công khai: đếm event sắp diễn ra theo category cho trang chủ/filter (plan P3 §2.3). */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryCountResponse>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(eventDiscoveryService.countByCategory()));
    }

    /** Event của host đang đăng nhập (mọi trạng thái). */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PagedResponse<EventResponse>>> mine(
            @AuthenticationPrincipal String userPublicId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.listMine(userPublicId, pageable)));
    }

    /** Công khai: chi tiết event. DRAFT chỉ chủ sở hữu xem được. */
    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<EventResponse>> detail(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getDetail(userPublicId, publicId)));
    }
}
