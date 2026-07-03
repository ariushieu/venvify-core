package com.venvify.venvifycore.notification.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.common.util.UuidV7;
import com.venvify.venvifycore.notification.dto.NotificationResponse;
import com.venvify.venvifycore.notification.entity.Notification;
import com.venvify.venvifycore.notification.enums.NotificationType;
import com.venvify.venvifycore.notification.mapper.NotificationMapper;
import com.venvify.venvifycore.notification.repository.NotificationRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Notification in-app (plan P6 §3). Nguồn ghi DUY NHẤT là {@link NotificationListener}
 * (nghe domain event — master §2); module nghiệp vụ KHÔNG gọi dispatch trực tiếp.
 * FE poll unread-count 30s — không WebSocket riêng cho việc này (MVP).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    // ---- ghi (gọi từ listener, REQUIRES_NEW vì chạy AFTER_COMMIT — tx gốc đã đóng) ----

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationType type, User recipient, String title, String content,
                         String refType, String refPublicId) {
        notificationRepository.save(Notification.builder()
                .user(recipient)
                .type(type)
                .title(title)
                .content(content)
                .relatedEntityType(refType)
                .relatedEntityPublicId(refPublicId)
                .build());
    }

    /**
     * Fan-out một nội dung cho nhiều user (follower của host — P6 §1): batch insert
     * JDBC một round-trip thay vì N câu qua JPA; public_id UUIDv7 sinh ở Java.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchBatch(NotificationType type, List<Long> recipientUserIds,
                              String title, String content, String refType, String refPublicId) {
        if (recipientUserIds.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate("""
                        insert into notifications
                          (public_id, user_id, type, title, content, is_read,
                           related_entity_type, related_entity_public_id, created_at, updated_at, version)
                        values (?, ?, ?, ?, ?, false, ?, ?, ?, ?, 0)""",
                recipientUserIds,
                recipientUserIds.size(),
                (ps, userId) -> {
                    ps.setString(1, UuidV7.generateString());
                    ps.setLong(2, userId);
                    ps.setString(3, type.name());
                    ps.setString(4, title);
                    ps.setString(5, content);
                    ps.setString(6, refType);
                    ps.setString(7, refPublicId);
                    ps.setTimestamp(8, now);
                    ps.setTimestamp(9, now);
                });
    }

    // ---- đọc / mutate của user (plan P6 §3) ----

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> listMine(String userPublicId, boolean unreadOnly,
                                                        int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        User user = userService.getByPublicId(userPublicId);
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByIdDesc(user.getId(), pageable)
                : notificationRepository.findByUserIdOrderByIdDesc(user.getId(), pageable);
        return PagedResponse.of(result.map(notificationMapper::toResponse));
    }

    @Transactional
    public NotificationResponse markRead(String userPublicId, String notificationPublicId) {
        Notification notification = notificationRepository.findByPublicId(notificationPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getPublicId().equals(userPublicId)) {
            // 404 thay vì 403: không xác nhận notification của người khác tồn tại (IDOR).
            throw new ResourceNotFoundException("Notification not found");
        }
        notification.setRead(true);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(String userPublicId) {
        User user = userService.getByPublicId(userPublicId);
        return notificationRepository.markAllRead(user.getId());
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userPublicId) {
        User user = userService.getByPublicId(userPublicId);
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }
}
