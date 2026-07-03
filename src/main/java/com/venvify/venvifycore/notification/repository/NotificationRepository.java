package com.venvify.venvifycore.notification.repository;

import com.venvify.venvifycore.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByPublicId(String publicId);

    /** R15: sort theo id (UUIDv7/auto-inc đơn điệu), không theo created_at. */
    Page<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalseOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    /** Đánh đã-đọc hàng loạt một câu — bulk bỏ qua @Version, chấp nhận (mutation một chiều). */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);

    /** Purge >90 ngày (master §8) — delete idempotent. */
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
