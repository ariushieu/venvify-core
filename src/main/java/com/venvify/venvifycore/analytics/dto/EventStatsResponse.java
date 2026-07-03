package com.venvify.venvifycore.analytics.dto;

import com.venvify.venvifycore.booking.enums.BookingStatus;

import java.util.Map;

/**
 * Thống kê 1 event cho host (P6 §5) — GET /events/{id}/stats.
 * attendanceRate (ATTENDED/CONFIRMED) đắp vào khi P4 có attendance (amend log P6).
 */
public record EventStatsResponse(
        String eventPublicId,
        Map<BookingStatus, Long> bookingsByStatus,
        long grossRevenue,
        long hostNetReleased,
        long pollCount,
        long pollVoteCount,
        long questionCount
) {
}
