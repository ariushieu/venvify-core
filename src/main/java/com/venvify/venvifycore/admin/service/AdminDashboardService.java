package com.venvify.venvifycore.admin.service;

import com.venvify.venvifycore.admin.dto.AdminDashboardResponse;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.service.TransactionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KPI tổng hợp (P6 §4) — đọc-only qua service các module, cache 60s (CacheConfig). */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserService userService;
    private final EventService eventService;
    private final TransactionQueryService transactionQueryService;

    @Cacheable(cacheNames = "adminDashboard")
    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                userService.countUsers(),
                userService.countHosts(),
                eventService.countByStatus(),
                eventService.countUpcomingWithinDays(7),
                transactionQueryService.grossMerchandiseValue(),
                transactionQueryService.systemJarBalance(WalletAccountType.COMMISSION),
                transactionQueryService.systemJarBalance(WalletAccountType.ESCROW));
    }
}
