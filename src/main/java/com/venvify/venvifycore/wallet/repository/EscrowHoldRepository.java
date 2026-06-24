package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.wallet.entity.EscrowHold;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscrowHoldRepository extends JpaRepository<EscrowHold, Long> {

    Optional<EscrowHold> findByPublicId(String publicId);

    Optional<EscrowHold> findByBookingId(Long bookingId);

    List<EscrowHold> findByEventIdAndStatus(Long eventId, EscrowStatus status);
}
