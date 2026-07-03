package com.venvify.venvifycore.event.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.common.util.SlugGenerator;
import com.venvify.venvifycore.event.domain.EventCancelledEvent;
import com.venvify.venvifycore.event.domain.EventPublishedEvent;
import com.venvify.venvifycore.event.dto.CreateEventRequest;
import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.dto.UpdateEventRequest;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.mapper.EventMapper;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.common.util.UuidV7;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.repository.UserRepository;
import com.venvify.venvifycore.wallet.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ vòng đời Event (plan §2). Ownership-based: chỉ host sở hữu mới sửa/đổi trạng thái.
 * Role HOST được cấp khi publish lần đầu (draft chỉ là nháp riêng tư); cổng KYC ở bước payout.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserRepository userRepository;
    private final EscrowService escrowService;
    private final ApplicationEventPublisher eventPublisher;

    /** Tạo event DRAFT. Chưa cấp HOST ở đây — role được cấp khi publish lần đầu (plan §1 E1, E4). */
    @Transactional
    public EventResponse create(String userPublicId, CreateEventRequest request) {
        User host = requireUser(userPublicId);

        Event event = eventMapper.toEntity(request);
        event.setHost(host);
        event.setStatus(EventStatus.DRAFT);
        event.setClaimedSlots(0);
        event.setSlug(generateUniqueSlug(request.title()));

        return eventMapper.toResponse(eventRepository.save(event));
    }

    /** Cập nhật event. DRAFT sửa thoải mái; PUBLISHED bị giới hạn (plan §5 B5). */
    @Transactional
    public EventResponse update(String userPublicId, String eventPublicId, UpdateEventRequest request) {
        Event event = requireOwnedEvent(userPublicId, eventPublicId);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
            throw new BadRequestException("Event can no longer be edited");
        }

        if (event.getStatus() == EventStatus.PUBLISHED) {
            if (request.priceAmount() != null && !request.priceAmount().equals(event.getPriceAmount())) {
                throw new BadRequestException("Price cannot be changed after publishing");
            }
            if (request.maxSlots() != null && request.maxSlots() < event.getClaimedSlots()) {
                throw new BadRequestException("Max slots cannot be lower than already claimed slots");
            }
            if (isRescheduling(request, event)) {
                throw new BadRequestException("Rescheduling a published event is not supported yet");
            }
        }

        eventMapper.updateEntity(request, event);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    /** DRAFT → PUBLISHED, bắt buộc đủ thời gian + timezone (plan §5 B2); cấp HOST cho chủ event. */
    @Transactional
    public EventResponse publish(String userPublicId, String eventPublicId) {
        Event event = requireOwnedEvent(userPublicId, eventPublicId);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BadRequestException("Only draft events can be published");
        }
        if (event.getStartTime() == null || event.getEndTime() == null || event.getTimezone() == null) {
            throw new BadRequestException("Start time, end time and timezone are required to publish");
        }
        if (!event.getStartTime().isAfter(Instant.now())) {
            throw new BadRequestException("Start time must be in the future");
        }
        if (!event.getEndTime().isAfter(event.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }
        if (event.getMaxSlots() == null || event.getMaxSlots() < 1) {
            throw new BadRequestException("Max slots must be at least 1");
        }

        event.setStatus(EventStatus.PUBLISHED);

        User host = event.getHost();
        if (host.getRoles().add(Role.HOST)) {
            userRepository.save(host);
        }

        Event saved = eventRepository.save(event);
        // Publish TRONG tx — listener AFTER_COMMIT (P6 fan-out follower) chỉ chạy khi commit thật.
        eventPublisher.publishEvent(new EventPublishedEvent(saved.getId()));
        return eventMapper.toResponse(saved);
    }

    /**
     * Huỷ event (DRAFT hoặc PUBLISHED). Vé PAID: hoàn 100% từ escrow về từng buyer, booking →
     * REFUNDED (money-core §3.3 — luồng refund DUY NHẤT). Vé free giữ nguyên CONFIRMED.
     * Khóa row event trước khi hủy để serialize với luồng mua vé (cũng khóa event — R13):
     * không có purchase nào "đang bay" chen vào giữa refund và đổi status.
     */
    @Transactional
    public EventResponse cancel(String userPublicId, String eventPublicId) {
        return doCancel(requireOwnedEvent(userPublicId, eventPublicId));
    }

    /**
     * Takedown của admin (P6 §4): force-cancel KHÔNG cần ownership — tái dùng nguyên luồng
     * refund money-core §3.3. AdminModerationService bọc tx + audit; notification attendee
     * đi cùng EventCancelledEvent như host tự hủy.
     */
    @Transactional
    public EventResponse cancelAsAdmin(String eventPublicId) {
        return doCancel(requireExistingEvent(eventPublicId));
    }

    private EventResponse doCancel(Event event) {
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
            throw new BadRequestException("Only draft or published events can be cancelled");
        }

        Event locked = eventRepository.findByIdForUpdate(event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        locked.setStatus(EventStatus.CANCELLED);
        escrowService.refundHeldForEvent(locked);

        // AFTER_COMMIT: booking hủy transfer PENDING (P3 §1.4), notification attendee (P6).
        eventPublisher.publishEvent(new EventCancelledEvent(locked.getId()));

        // MVP chạy đồng bộ trong cùng tx — event nghìn vé thì chuyển job async (ghi chú plan §3.3).
        return eventMapper.toResponse(eventRepository.save(locked));
    }

    /** Soft delete (plan §2): chỉ cho phép với DRAFT hoặc CANCELLED. */
    @Transactional
    public void delete(String userPublicId, String eventPublicId) {
        Event event = requireOwnedEvent(userPublicId, eventPublicId);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new BadRequestException("Only draft or cancelled events can be deleted");
        }

        event.setDeleted(true);
        eventRepository.save(event);
    }

    /** Event của chính host (mọi trạng thái). List công khai đã sang EventDiscoveryService (P3). */
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listMine(String userPublicId, Pageable pageable) {
        User host = requireUser(userPublicId);
        Page<Event> page = eventRepository.findByHostIdAndDeletedFalse(host.getId(), pageable);
        return PagedResponse.of(page.map(eventMapper::toResponse));
    }

    /** Chi tiết event. DRAFT chỉ chủ sở hữu xem được (plan §2). {@code viewerPublicId} null = khách. */
    @Transactional(readOnly = true)
    public EventResponse getDetail(String viewerPublicId, String eventPublicId) {
        Event event = requireExistingEvent(eventPublicId);

        if (event.getStatus() == EventStatus.DRAFT && !isOwner(event, viewerPublicId)) {
            throw new ResourceNotFoundException("Event not found");
        }

        return eventMapper.toResponse(event);
    }

    // ---- admin (P6 §4) ----

    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> adminSearch(String q, EventStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        String normalized = (q == null || q.isBlank()) ? null : q.trim();
        Page<Event> result = eventRepository.adminSearch(normalized, status, PageRequest.of(page, size));
        return PagedResponse.of(result.map(eventMapper::toResponse));
    }

    /** KPI dashboard — đếm theo status. */
    @Transactional(readOnly = true)
    public Map<EventStatus, Long> countByStatus() {
        Map<EventStatus, Long> counts = new EnumMap<>(EventStatus.class);
        eventRepository.countByStatusGrouped().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    /** Host analytics (P6 §5) — tổng event chưa xóa của host. */
    @Transactional(readOnly = true)
    public long countByHost(Long hostId) {
        return eventRepository.countByHostIdAndDeletedFalse(hostId);
    }

    /** KPI dashboard — event PUBLISHED bắt đầu trong {@code days} ngày tới. */
    @Transactional(readOnly = true)
    public long countUpcomingWithinDays(int days) {
        Instant now = Instant.now();
        return eventRepository.countByStatusAndDeletedFalseAndStartTimeBetween(
                EventStatus.PUBLISHED, now, now.plus(Duration.ofDays(days)));
    }

    /** Lookup entity cho module khác (social review…) — 404 nếu không có/đã xóa. */
    @Transactional(readOnly = true)
    public Event loadByPublicId(String eventPublicId) {
        return requireExistingEvent(eventPublicId);
    }

    /** Đọc cho NotificationListener (master §2 amend 2026-07-04) — fetch join host, không lazy leak. */
    @Transactional(readOnly = true)
    public Event loadWithHost(Long eventId) {
        return eventRepository.findWithHostByIdIn(List.of(eventId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));
    }

    // ----- helpers -----

    private User requireUser(String userPublicId) {
        return userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Event requireExistingEvent(String eventPublicId) {
        Event event = eventRepository.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (event.isDeleted()) {
            throw new ResourceNotFoundException("Event not found");
        }
        return event;
    }

    private Event requireOwnedEvent(String userPublicId, String eventPublicId) {
        Event event = requireExistingEvent(eventPublicId);
        if (!isOwner(event, userPublicId)) {
            throw new ForbiddenException("You do not own this event");
        }
        return event;
    }

    private boolean isOwner(Event event, String userPublicId) {
        return userPublicId != null && event.getHost().getPublicId().equals(userPublicId);
    }

    private boolean isRescheduling(UpdateEventRequest request, Event event) {
        boolean startChanged = request.startTime() != null && !request.startTime().equals(event.getStartTime());
        boolean endChanged = request.endTime() != null && !request.endTime().equals(event.getEndTime());
        return startChanged || endChanged;
    }

    /** Slug từ title; nếu trùng thì thêm hậu tố ngắn. Unique constraint trên cột slug là chốt cuối. */
    private String generateUniqueSlug(String title) {
        String base = SlugGenerator.toSlug(title);
        if (base.isBlank()) {
            base = "event";
        }
        String slug = base;
        while (eventRepository.existsBySlug(slug)) {
            slug = base + "-" + UuidV7.generateString().substring(0, 8);
        }
        return slug;
    }
}
