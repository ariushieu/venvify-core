package com.venvify.venvifycore.event.domain;

/**
 * Domain event: event vừa bị hủy (host cancel hoặc admin takedown) — publish TRONG tx,
 * listener chạy AFTER_COMMIT (master §2): booking hủy transfer PENDING, notification
 * báo attendee kèm refund. Chỉ mang id — listener tự load ở tx riêng của nó.
 */
public record EventCancelledEvent(Long eventId) {
}
