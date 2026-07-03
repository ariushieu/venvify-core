package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.wallet.entity.EscrowHold;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
