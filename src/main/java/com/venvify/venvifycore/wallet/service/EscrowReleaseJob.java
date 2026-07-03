package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import com.venvify.venvifycore.wallet.repository.EscrowHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Job release escrow (money-core §3.4), chạy mỗi 15 phút:
 * <ol>
 *   <li>Auto-end: event PUBLISHED/LIVE quá {@code end_time} → ENDED (bulk, tx riêng ở repo).</li>
 *   <li>Release: hold HELD của event ENDED quá dispute window ({@code escrow-release-delay-days},
 *       O-M3 — trong cửa sổ đó admin còn hủy event → refund được trước khi tiền rời ESCROW)
 *       → chia ví HOST + hũ COMMISSION. Mỗi hold 1 transaction riêng — 1 hold lỗi không chặn
 *       hold khác; idempotent vì chỉ quét HELD và {@code releaseHold} re-check status.</li>
 * </ol>
 * Method này KHÔNG transactional có chủ đích — mỗi việc con tự mở tx của nó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscrowReleaseJob {

    private static final List<EventStatus> AUTO_END_FROM = List.of(EventStatus.PUBLISHED, EventStatus.LIVE);

    private final EventRepository eventRepository;
    private final EscrowHoldRepository escrowHoldRepository;
    private final EscrowService escrowService;

    /** O-M3 — dispute window sau ENDED. */
    @Value("${app.money.escrow-release-delay-days:3}")
    private int releaseDelayDays;

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void run() {
        Instant now = Instant.now();

        int ended = eventRepository.markEnded(EventStatus.ENDED, AUTO_END_FROM, now);
        if (ended > 0) {
            log.info("Auto-ended {} event(s) past their end_time", ended);
        }

        Instant cutoff = now.minus(Duration.ofDays(releaseDelayDays));
        List<Long> holdIds = escrowHoldRepository.findReleasableHoldIds(
                EscrowStatus.HELD, EventStatus.ENDED, cutoff);
        int released = 0;
        for (Long holdId : holdIds) {
            try {
                escrowService.releaseHold(holdId);
                released++;
            } catch (Exception ex) {
                // Không rethrow: hold lỗi nằm lại HELD, lần chạy sau thử tiếp; các hold khác vẫn đi.
                log.error("Failed to release escrow hold {}: {}", holdId, ex.getMessage(), ex);
            }
        }
        if (!holdIds.isEmpty()) {
            log.info("Escrow release: {}/{} hold(s) released", released, holdIds.size());
        }
    }
}
