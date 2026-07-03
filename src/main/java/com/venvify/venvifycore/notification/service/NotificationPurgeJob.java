package com.venvify.venvifycore.notification.service;

import com.venvify.venvifycore.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Purge notification >90 ngày — 04:41 Chủ nhật (master §8); delete idempotent. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPurgeJob {

    static final Duration RETENTION = Duration.ofDays(90);

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 41 4 * * SUN")
    @Transactional
    public void run() {
        int purged = notificationRepository.deleteOlderThan(Instant.now().minus(RETENTION));
        if (purged > 0) {
            log.info("Notification purge: deleted {} notification(s) older than {} days",
                    purged, RETENTION.toDays());
        }
    }
}
