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
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, EventSearchRepository {

    Optional<Event> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.publicId = :publicId")
    Optional<Event> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<Event> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findByHostIdAndDeletedFalse(Long hostId, Pageable pageable);

    // ---- storefront (plan P3 §2.4): upcoming = PUBLISHED chưa tới giờ; past = ENDED ----

    Page<Event> findByHostIdAndStatusAndDeletedFalseAndStartTimeGreaterThanEqual(
            Long hostId, EventStatus status, Instant now, Pageable pageable);

    Page<Event> findByHostIdAndStatusAndDeletedFalse(Long hostId, EventStatus status, Pageable pageable);

    long countByHostIdAndStatusAndDeletedFalseAndStartTimeGreaterThanEqual(
            Long hostId, EventStatus status, Instant now);

    /**
     * Bước 2 của 2-query pattern discovery: load entity theo trang IDs từ
     * {@link EventSearchRepository#searchIds} — fetch join host, caller tự xếp lại theo thứ tự ids.
     */
    @Query("select e from Event e join fetch e.host where e.id in :ids")
    List<Event> findWithHostByIdIn(@Param("ids") Collection<Long> ids);

    /** Count theo category cho trang chủ (plan P3 §2.3) — GROUP BY một câu, cache 60s ở service. */
    @Query("""
            select e.category as category, count(e) as total
            from Event e
            where e.status = :status and e.deleted = false and e.startTime > :now and e.category is not null
            group by e.category""")
    List<CategoryCount> countUpcomingByCategory(@Param("status") EventStatus status, @Param("now") Instant now);

    /** Projection cho {@link #countUpcomingByCategory}. */
    interface CategoryCount {
        EventCategory getCategory();

        long getTotal();
    }

    // ---- admin (P6 §4) ----

    /** Search CSKH theo title/slug + filter status; null = bỏ filter. Soft-deleted vẫn hiện (admin). */
    @Query(value = """
            select e from Event e join fetch e.host
            where (:status is null or e.status = :status)
              and (:q is null or lower(e.title) like lower(concat('%', :q, '%'))
                   or lower(e.slug) like lower(concat('%', :q, '%')))
            order by e.id desc""",
            countQuery = """
            select count(e) from Event e
            where (:status is null or e.status = :status)
              and (:q is null or lower(e.title) like lower(concat('%', :q, '%'))
                   or lower(e.slug) like lower(concat('%', :q, '%')))""")
    Page<Event> adminSearch(@Param("q") String q, @Param("status") EventStatus status, Pageable pageable);

    /** KPI dashboard: đếm theo status một câu GROUP BY. */
    @Query("select e.status as status, count(e) as total from Event e where e.deleted = false group by e.status")
    List<StatusCount> countByStatusGrouped();

    interface StatusCount {
        EventStatus getStatus();

        long getTotal();
    }

    /** KPI: event sắp diễn ra trong N ngày tới. */
    long countByStatusAndDeletedFalseAndStartTimeBetween(EventStatus status, Instant from, Instant to);

    /** Host analytics (P6 §5). */
    long countByHostIdAndDeletedFalse(Long hostId);

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
