package com.venvify.venvifycore.wallet.dto;

public record WalletResponse(
        String publicId,
        Long balance,
        String currency
) {
}
