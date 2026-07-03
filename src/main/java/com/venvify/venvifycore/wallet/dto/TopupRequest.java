package com.venvify.venvifycore.wallet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Nạp ví dev-only (money-core §3.6). VND nguyên; trần 100 triệu/lần — công cụ test, không hơn. */
public record TopupRequest(
        @NotNull @Positive @Max(100_000_000) Long amount
) {
}
