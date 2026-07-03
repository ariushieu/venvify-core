package com.venvify.venvifycore.wallet.enums;

public enum TransactionType {
    TOPUP,
    TICKET_PURCHASE,
    /** Bán lại vé P2P (D9/D10) — ví receiver → ví sender, escrow gốc KHÔNG đụng (plan P3 §1.3). */
    TICKET_RESALE,
    REFUND,
    PAYOUT,
    COMMISSION,
    /** Bút toán đảo sửa sai sự cố (F3) — công cụ vận hành, chưa có endpoint tạo. */
    REVERSAL
}
