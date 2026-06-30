package com.venvify.venvifycore.wallet.enums;

/**
 * Loại tài khoản trong sổ kép (double-entry, D12).
 * {@code USER} = ví của một user; các giá trị còn lại là hũ hệ thống (user_id = null).
 */
public enum WalletAccountType {
    /** Ví của một user. */
    USER,
    /** Tiền vé đang giữ chờ event xong (đối soát với escrow_holds). */
    ESCROW,
    /** Phí platform được hưởng. */
    COMMISSION,
    /** Ranh giới với ngân hàng thật (Sepay) — tiền ra/vào hệ thống đi qua đây. */
    BANK_CLEARING,
    /** Tiền chưa khớp transaction_ref, chờ xử lý tay (không tự cộng vào đâu). */
    SUSPENSE
}
