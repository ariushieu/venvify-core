package com.venvify.venvifycore.notification.enums;

/**
 * ⚠ Giá trị mới phải ≤30 ký tự — cột {@code notifications.type varchar(30)} (V5).
 * Thêm giá trị = thêm hằng + cập nhật enum ledger (master §6), KHÔNG cần DDL.
 */
public enum NotificationType {
    BOOKING_CONFIRMED,
    PAYMENT_RECEIPT,
    EVENT_REMINDER,
    EVENT_CANCELLED,
    EVENT_POSTPONED,
    EVENT_UPDATED,
    NEW_EVENT_FROM_FOLLOWED_HOST,
    // Nhóm transfer (P3+P6 2026-07-04) — người nhận thông báo tùy trạng thái (xem NotificationListener).
    TRANSFER_OFFER_RECEIVED,
    TRANSFER_COMPLETED,
    TRANSFER_DECLINED,
    TRANSFER_CANCELLED,
    TRANSFER_EXPIRED
}
