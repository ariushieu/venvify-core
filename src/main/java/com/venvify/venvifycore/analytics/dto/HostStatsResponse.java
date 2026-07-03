package com.venvify.venvifycore.analytics.dto;

/** Tổng quan của host (P6 §5) — GET /users/me/host-stats. */
public record HostStatsResponse(
        long totalEvents,
        long totalAttendees,
        long releasedRevenue,
        long followerCount,
        double avgRating
) {
}
