package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.util.UuidV7;

/**
 * Sinh transaction_ref nội bộ: {@code PREFIX-UUIDv7} (money-core plan §2).
 * Prefix hiện dùng: TKT (mua vé), RFD (refund), REL (release escrow), TOP (top-up), RSL (resale).
 * Format ngắn alnum-only nhúng được vào nội dung chuyển khoản là việc của slice Sepay.
 */
final class TransactionRefs {

    private TransactionRefs() {
    }

    static String next(String prefix) {
        return prefix + "-" + UuidV7.generateString();
    }
}
