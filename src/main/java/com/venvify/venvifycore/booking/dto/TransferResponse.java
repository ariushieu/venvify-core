package com.venvify.venvifycore.booking.dto;

import com.venvify.venvifycore.booking.enums.TicketTransferStatus;

import java.time.Instant;

public record TransferResponse(
        String publicId,
        String bookingPublicId,
        String eventPublicId,
        String eventTitle,
        String fromUserPublicId,
        String fromUserName,
        String toUserPublicId,
        String toUserName,
        Long price,
        TicketTransferStatus status,
        String transactionRef,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt
) {
}
