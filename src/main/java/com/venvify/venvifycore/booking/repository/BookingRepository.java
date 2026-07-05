package com.venvify.venvifycore.booking.repository;

import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPublicId(String publicId);

    /**
     * Snapshot id-only trước khi khóa theo thứ tự Event → Booking. Không load entity vào
     * persistence context, tránh dùng state cũ làm nguồn sự thật khi tới bước FOR UPDATE.
     */
    @Query("select b.id as id, b.event.id as eventId from Booking b where b.publicId = :publicId")
    Optional<BookingLockIds> findLockIdsByPublicId(@Param("publicId") String publicId);

    interface BookingLockIds {
        Long getId();

        Long getEventId();
    }

    /**
     * Khóa row booking — luồng transfer (tạo offer R-T1, accept) mutate attendee/transfer_count
     * an toàn trước race 2 accept song song. Thứ tự khóa toàn cục: Event → Booking → Wallet (§7).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    Optional<Booking> findByEventIdAndAttendeeId(Long eventId, Long attendeeId);

    /** Dùng cho internal API authorize vào room (SPEC §5.2). */
    boolean existsByEventIdAndAttendeeIdAndStatusIn(Long eventId, Long attendeeId, Collection<BookingStatus> statuses);

    Page<Booking> findByAttendeeId(Long attendeeId, Pageable pageable);

    long countByEventIdAndStatusIn(Long eventId, Collection<BookingStatus> statuses);

    /** Attendee cần báo khi event bị hủy (notification listener) — fetch sẵn người + event. */
    @EntityGraph(attributePaths = {"attendee", "event"})
    List<Booking> findByEventIdAndStatusIn(Long eventId, Collection<BookingStatus> statuses);

    // ---- host analytics (P6 §5 — thuần SELECT) ----

    @Query("select b.status as status, count(b) as total from Booking b where b.event.id = :eventId group by b.status")
    List<StatusCount> countByStatusGroupedForEvent(@Param("eventId") Long eventId);

    interface StatusCount {
        BookingStatus getStatus();

        long getTotal();
    }

    /** Tổng attendee mọi event của host (vé đang sống: CONFIRMED/ATTENDED). */
    @Query("select count(b) from Booking b where b.event.host.id = :hostId and b.status in :statuses")
    long countForHostByStatuses(@Param("hostId") Long hostId,
                                @Param("statuses") Collection<BookingStatus> statuses);
}
