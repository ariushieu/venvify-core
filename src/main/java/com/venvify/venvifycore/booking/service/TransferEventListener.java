package com.venvify.venvifycore.booking.service;

import com.venvify.venvifycore.event.domain.EventCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Booking nghe domain event của module event (ma trận §2 — event KHÔNG gọi booking trực tiếp).
 * AFTER_COMMIT: chỉ chạy khi cancel + refund đã commit thật; offer PENDING trong khe hở
 * commit→listener cũng không accept được (accept re-check event status dưới khóa).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventListener {

    private final TransferService transferService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCancelled(EventCancelledEvent event) {
        try {
            transferService.cancelPendingForEvent(event.eventId());
        } catch (Exception e) {
            // Side-effect sau commit: không được ném ngược làm caller tưởng cancel fail.
            log.error("Failed to cancel pending transfers of event {}", event.eventId(), e);
        }
    }
}
