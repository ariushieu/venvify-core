package com.venvify.venvifycore.event.repository;

import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByPublicId(String publicId);

    Optional<Event> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findByStatusAndDeletedFalse(EventStatus status, Pageable pageable);

    Page<Event> findByHostIdAndDeletedFalse(Long hostId, Pageable pageable);

    /** Khóa row event để cập nhật claimed_slots an toàn khi nhiều người claim đồng thời (D4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
