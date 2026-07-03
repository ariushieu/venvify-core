package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.wallet.dto.TransactionAdminResponse;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.EscrowHoldRepository;
import com.venvify.venvifycore.wallet.repository.TransactionRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Đọc-only cho admin (P6 §4): tra cứu transaction CSKH/đối soát + KPI tiền cho dashboard.
 * Không mutation nào ở đây — mọi chuyển động tiền vẫn chỉ qua LedgerService.
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final EscrowHoldRepository escrowHoldRepository;

    @Transactional(readOnly = true)
    public PagedResponse<TransactionAdminResponse> adminSearch(String ref, TransactionType type,
                                                               String userPublicId, Instant from, Instant to,
                                                               int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        return PagedResponse.of(transactionRepository
                .adminSearch(blankToNull(ref), type, blankToNull(userPublicId), from, to,
                        PageRequest.of(page, size))
                .map(TransactionQueryService::toAdminResponse));
    }

    /** KPI GMV: tổng TICKET_PURCHASE + TICKET_RESALE đã SUCCESS. */
    @Transactional(readOnly = true)
    public long grossMerchandiseValue() {
        return transactionRepository.sumAmountByTypeInAndStatus(
                List.of(TransactionType.TICKET_PURCHASE, TransactionType.TICKET_RESALE),
                TransactionStatus.SUCCESS);
    }

    /** KPI: số dư hũ hệ thống (COMMISSION đã thực thu, ESCROW đang giữ…). */
    @Transactional(readOnly = true)
    public long systemJarBalance(WalletAccountType type) {
        return walletRepository.findByAccountType(type)
                .map(Wallet::getBalanceCached)
                .orElseThrow(() -> new IllegalStateException("System wallet missing: " + type));
    }

    // ---- host analytics (P6 §5) ----

    /** Doanh thu host đã THẬT SỰ về ví (escrow RELEASED, phần host_net). */
    @Transactional(readOnly = true)
    public long releasedHostNetForHost(Long hostId) {
        return escrowHoldRepository.sumHostNetByHostAndStatus(hostId, EscrowStatus.RELEASED);
    }

    /** Doanh thu 1 event: gross đã bán (HELD + RELEASED — không tính refund) + net đã release. */
    @Transactional(readOnly = true)
    public EventRevenue revenueForEvent(Long eventId) {
        long gross = escrowHoldRepository.sumGrossByEventAndStatuses(
                eventId, List.of(EscrowStatus.HELD, EscrowStatus.RELEASED));
        long netReleased = escrowHoldRepository.sumHostNetByEventAndStatus(eventId, EscrowStatus.RELEASED);
        return new EventRevenue(gross, netReleased);
    }

    public record EventRevenue(long grossSold, long hostNetReleased) {
    }

    // ----- helpers -----

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static TransactionAdminResponse toAdminResponse(Transaction txn) {
        return new TransactionAdminResponse(
                txn.getPublicId(),
                txn.getType(),
                txn.getStatus(),
                txn.getAmount(),
                txn.getTransactionRef(),
                txn.getPaymentProvider(),
                txn.getUser().getPublicId(),
                txn.getUser().getEmail(),
                txn.getEvent() == null ? null : txn.getEvent().getPublicId(),
                txn.getCreatedAt(),
                txn.getCompletedAt());
    }
}
