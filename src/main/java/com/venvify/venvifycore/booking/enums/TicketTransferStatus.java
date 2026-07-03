package com.venvify.venvifycore.booking.enums;

/**
 * State machine của offer chuyển nhượng vé (plan P3 §1.1):
 * PENDING → COMPLETED (receiver accept + trả tiền nếu có giá)
 *         → CANCELLED (sender rút lại) → DECLINED (receiver từ chối) → EXPIRED (quá 72h — job).
 */
public enum TicketTransferStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    DECLINED,
    EXPIRED
}
