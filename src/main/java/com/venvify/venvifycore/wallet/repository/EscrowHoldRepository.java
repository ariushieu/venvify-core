package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.wallet.entity.EscrowHold;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EscrowHoldRepository extends JpaRepository<EscrowHold, Long> {

    Optional<EscrowHold> findByPublicId(String publicId);

    /**
     * Tra hold theo booking + status (F6). KHÔNG dùng findByBookingId trần: booking refund xong
     * mua lại sẽ có 2 hold (REFUNDED + HELD) → Optional nổ. Service đảm bảo ≤ 1 hold HELD/booking
     * (guard dưới khóa event).
     */
    Optional<EscrowHold> findByBookingIdAndStatus(Long bookingId, EscrowStatus status);

    List<EscrowHold> findByEventIdAndStatus(Long eventId, EscrowStatus status);

    /**
     * Hold đủ điều kiện release (money-core §3.4): còn HELD, event đã ENDED và kết thúc trước
     * {@code cutoff} (= now − dispute window). Trả id để job mở transaction riêng cho từng hold.
     */
    @Query("select h.id from EscrowHold h "
            + "where h.status = :held and h.event.status = :ended and h.event.endTime < :cutoff")
    List<Long> findReleasableHoldIds(@Param("held") EscrowStatus held,
                                     @Param("ended") EventStatus ended,
                                     @Param("cutoff") Instant cutoff);

    /** Bất biến 4 (reconcile §4): số dư hũ ESCROW phải bằng tổng gross các hold HELD. */
    @Query("select coalesce(sum(h.grossAmount), 0) from EscrowHold h where h.status = :status")
    long sumGrossByStatus(@Param("status") EscrowStatus status);
}
