package com.venvify.venvifycore.booking.repository;

import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPublicId(String publicId);

    Optional<Booking> findByEventIdAndAttendeeId(Long eventId, Long attendeeId);

    /** Dùng cho internal API authorize vào room (SPEC §5.2). */
    boolean existsByEventIdAndAttendeeIdAndStatusIn(Long eventId, Long attendeeId, Collection<BookingStatus> statuses);

    Page<Booking> findByAttendeeId(Long attendeeId, Pageable pageable);

    long countByEventIdAndStatusIn(Long eventId, Collection<BookingStatus> statuses);
}
