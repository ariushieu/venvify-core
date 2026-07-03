package com.venvify.venvifycore.wallet.dto;

import com.venvify.venvifycore.wallet.enums.TransactionType;

import java.time.Instant;

/** Một dòng sao kê ví (money-core §3.5): bút toán + ngữ cảnh transaction của nó. */
public record LedgerEntryResponse(
        String publicId,
        Long amount,
        Long balanceAfter,
        String description,
        TransactionType transactionType,
        String transactionRef,
        Instant createdAt
) {
}
