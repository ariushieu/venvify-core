package com.venvify.venvifycore.admin.service;

import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.social.entity.Review;
import com.venvify.venvifycore.social.service.ReviewService;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.mapper.UserMapper;
import com.venvify.venvifycore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mutation của admin (P6 §4) — service MỎNG: nghiệp vụ nằm ở module chủ (gọi qua service
 * công khai, ma trận §2), đây chỉ bọc 1 use-case = 1 transaction + audit CÙNG tx.
 */
@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final UserService userService;
    private final EventService eventService;
    private final ReviewService reviewService;
    private final AuditService auditService;
    private final UserMapper userMapper;

    /** Ban = SUSPENDED + revoke toàn bộ refresh token (UserService); login sau đó 401 generic. */
    @Transactional
    public UserResponse banUser(String adminPublicId, String targetPublicId, String reason) {
        User admin = userService.getByPublicId(adminPublicId);
        User target = userService.ban(targetPublicId);
        auditService.record(admin, "USER_BAN", "USER", target.getPublicId(), reason);
        return userMapper.toResponse(target);
    }

    @Transactional
    public UserResponse unbanUser(String adminPublicId, String targetPublicId) {
        User admin = userService.getByPublicId(adminPublicId);
        User target = userService.unban(targetPublicId);
        auditService.record(admin, "USER_UNBAN", "USER", target.getPublicId(), null);
        return userMapper.toResponse(target);
    }

    /**
     * Takedown = force-cancel (mọi trạng thái DRAFT/PUBLISHED, không cần ownership) — tái dùng
     * NGUYÊN luồng refund money-core §3.3; attendee nhận notification + email qua
     * EventCancelledEvent như host tự hủy.
     */
    @Transactional
    public EventResponse takedownEvent(String adminPublicId, String eventPublicId, String reason) {
        User admin = userService.getByPublicId(adminPublicId);
        EventResponse cancelled = eventService.cancelAsAdmin(eventPublicId);
        auditService.record(admin, "EVENT_TAKEDOWN", "EVENT", eventPublicId, reason);
        return cancelled;
    }

    @Transactional
    public void hideReview(String adminPublicId, String reviewPublicId, String reason) {
        User admin = userService.getByPublicId(adminPublicId);
        Review review = reviewService.setHidden(reviewPublicId, true);
        auditService.record(admin, "REVIEW_HIDE", "REVIEW", review.getPublicId(), reason);
    }

    @Transactional
    public void unhideReview(String adminPublicId, String reviewPublicId) {
        User admin = userService.getByPublicId(adminPublicId);
        Review review = reviewService.setHidden(reviewPublicId, false);
        auditService.record(admin, "REVIEW_UNHIDE", "REVIEW", review.getPublicId(), null);
    }
}
