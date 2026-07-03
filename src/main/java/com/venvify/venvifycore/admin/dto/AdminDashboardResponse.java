package com.venvify.venvifycore.admin.dto;

import com.venvify.venvifycore.event.enums.EventStatus;

import java.util.Map;

/**
 * KPI dashboard (P6 §4) — toàn số đếm/tổng trên data sẵn, cache 60s.
 * Payout pending + suspense balance đắp vào khi P2 lên (amend log P6).
 */
public record AdminDashboardResponse(
        long totalUsers,
        long totalHosts,
        Map<EventStatus, Long> eventsByStatus,
        long upcomingEvents7d,
        long grossMerchandiseValue,
        long commissionBalance,
        long escrowBalance
) {
}
