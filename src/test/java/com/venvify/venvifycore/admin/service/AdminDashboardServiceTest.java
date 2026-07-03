package com.venvify.venvifycore.admin.service;

import com.venvify.venvifycore.admin.dto.AdminDashboardResponse;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.service.TransactionQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;
    @Mock
    private TransactionQueryService transactionQueryService;

    @InjectMocks
    private AdminDashboardService service;

    @Test
    void dashboard_composesAllKpis() {
        when(userService.countUsers()).thenReturn(120L);
        when(userService.countHosts()).thenReturn(15L);
        when(eventService.countByStatus()).thenReturn(Map.of(EventStatus.PUBLISHED, 8L));
        when(eventService.countUpcomingWithinDays(7)).thenReturn(3L);
        when(transactionQueryService.grossMerchandiseValue()).thenReturn(5_000_000L);
        when(transactionQueryService.systemJarBalance(WalletAccountType.COMMISSION)).thenReturn(250_000L);
        when(transactionQueryService.systemJarBalance(WalletAccountType.ESCROW)).thenReturn(1_000_000L);

        AdminDashboardResponse result = service.dashboard();

        assertThat(result.totalUsers()).isEqualTo(120);
        assertThat(result.totalHosts()).isEqualTo(15);
        assertThat(result.eventsByStatus()).containsEntry(EventStatus.PUBLISHED, 8L);
        assertThat(result.upcomingEvents7d()).isEqualTo(3);
        assertThat(result.grossMerchandiseValue()).isEqualTo(5_000_000L);
        assertThat(result.commissionBalance()).isEqualTo(250_000L);
        assertThat(result.escrowBalance()).isEqualTo(1_000_000L);
    }
}
