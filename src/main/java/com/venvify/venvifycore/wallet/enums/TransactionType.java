package com.venvify.venvifycore.wallet.enums;

public enum TransactionType {
    TOPUP,
    TICKET_PURCHASE,
    REFUND,
    PAYOUT,
    COMMISSION,
    /** Bút toán đảo sửa sai sự cố (F3) — công cụ vận hành, chưa có endpoint tạo. */
    REVERSAL
}
