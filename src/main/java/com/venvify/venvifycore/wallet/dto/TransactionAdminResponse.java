package com.venvify.venvifycore.wallet.dto;

import com.venvify.venvifycore.wallet.enums.PaymentProvider;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;

import java.time.Instant;

/** Dòng transaction cho màn admin CSKH/đối soát (P6 §4) — đọc-only. */
public record TransactionAdminResponse(
        String publicId,
        TransactionType type,
        TransactionStatus status,
        Long amount,
        String transactionRef,
        PaymentProvider paymentProvider,
        String userPublicId,
        String userEmail,
        String eventPublicId,
        Instant createdAt,
        Instant completedAt
) {
}
