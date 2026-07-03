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
}
