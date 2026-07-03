package com.venvify.venvifycore.booking.service;

import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import com.venvify.venvifycore.booking.repository.TicketTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Đánh EXPIRED offer PENDING quá hạn 72h (plan P3 §1.1, §5 — đã ghi vào jobs catalog master §8).
 * Pattern EscrowReleaseJob: job KHÔNG transactional, claim ids rồi xử lý từng offer 1 tx riêng —
 * 1 offer lỗi không chặn phần còn lại; chạy lại không đúp (re-check trong tx + @Version).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferExpiryJob {

    private final TicketTransferRepository transferRepository;
    private final TransferService transferService;

    /** 15' lệch pha với EscrowReleaseJob (PT1M) để hai job không thức dậy cùng nhịp. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT4M")
    public void run() {
        List<Long> expired = transferRepository.findExpiredPendingIds(
                TicketTransferStatus.PENDING, Instant.now());
        for (Long id : expired) {
            try {
                transferService.expireOne(id);
            } catch (Exception e) {
                log.error("Failed to expire transfer {}", id, e);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Transfer expiry: processed {} offer(s)", expired.size());
        }
    }
}
