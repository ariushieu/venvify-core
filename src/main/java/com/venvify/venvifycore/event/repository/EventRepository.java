package com.venvify.venvifycore.event.repository;

import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByPublicId(String publicId);

    Optional<Event> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findByStatusAndDeletedFalse(EventStatus status, Pageable pageable);

    Page<Event> findByStatusAndCategoryAndDeletedFalse(EventStatus status, EventCategory category, Pageable pageable);

    Page<Event> findByHostIdAndDeletedFalse(Long hostId, Pageable pageable);

    /** Khóa row event để cập nhật claimed_slots an toàn khi nhiều người claim đồng thời (D4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    /**
     * Auto-end (money-core §3.4): event quá giờ kết thúc → ENDED, bulk một câu — transition
     * ENDED đầu tiên của hệ thống, EscrowReleaseJob gọi mỗi 15 phút. Tx riêng tại repo vì
     * job caller cố ý KHÔNG transactional (mỗi việc của job là 1 tx độc lập).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Event e set e.status = :ended where e.status in :active and e.endTime < :now")
    int markEnded(@Param("ended") EventStatus ended,
                  @Param("active") Collection<EventStatus> active,
                  @Param("now") Instant now);
}
