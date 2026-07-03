package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.email.EmailService;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.EscrowHoldRepository;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository.TransactionSum;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository.WalletSum;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Đối soát sổ kép hằng đêm (money-core §4) — 4 bất biến của triết lý "không sai một xu".
 * Lệch bất kỳ = <b>sự cố P0</b>: log ERROR chi tiết + email admin NGAY (không dừng ở log —
 * amend review 2026-07-02). Job chỉ ĐỌC, không tự sửa: bút toán đảo là quyết định của người.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final EscrowHoldRepository escrowHoldRepository;
    private final EmailService emailService;

    /** 03:17 — lệch giờ chẵn để không giẫm backup cron. Gọi thẳng {@code reconcile()} khi cần chạy tay. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional(readOnly = true)
    public List<String> reconcile() {
        List<String> violations = new ArrayList<>();

        // 1. Tổng toàn bộ sổ = 0 (mọi transaction đều là cặp/split cân).
        long total = ledgerEntryRepository.sumAll();
        if (total != 0) {
            violations.add("Ledger grand total = " + total + ", expected 0");
        }

        // 2. Từng ví: balance_cached == SUM(ledger theo ví). Ví chưa có bút toán phải = 0.
        Map<Long, Long> ledgerByWallet = ledgerEntryRepository.sumGroupByWallet().stream()
                .collect(Collectors.toMap(WalletSum::getWalletId, WalletSum::getTotal));
        for (Wallet wallet : walletRepository.findAll()) {
            long expected = ledgerByWallet.getOrDefault(wallet.getId(), 0L);
            if (wallet.getBalanceCached() != expected) {
                violations.add("Wallet " + wallet.getId() + " (" + wallet.getAccountType()
                        + "): balance_cached = " + wallet.getBalanceCached() + ", ledger = " + expected);
            }
        }

        // 3. Từng transaction: tổng bút toán = 0.
        for (TransactionSum txn : ledgerEntryRepository.findUnbalancedTransactions()) {
            violations.add("Transaction " + txn.getTransactionId()
                    + ": entries sum = " + txn.getTotal() + ", expected 0");
        }

        // 4. Hũ ESCROW == tổng gross các hold HELD (tiền giữ hộ phải nằm đủ trong hũ).
        long heldGross = escrowHoldRepository.sumGrossByStatus(EscrowStatus.HELD);
        Wallet escrowJar = walletRepository.findByAccountType(WalletAccountType.ESCROW)
                .orElseThrow(() -> new IllegalStateException("ESCROW system wallet missing"));
        if (escrowJar.getBalanceCached() != heldGross) {
            violations.add("ESCROW jar balance = " + escrowJar.getBalanceCached()
                    + ", HELD holds gross = " + heldGross);
        }

        if (violations.isEmpty()) {
            log.info("Ledger reconciliation clean (4 invariants OK)");
        } else {
            violations.forEach(v -> log.error("[P0][reconcile] {}", v));
            emailService.sendOpsAlert(
                    "[P0] Venvify ledger reconciliation FAILED — " + violations.size() + " violation(s)",
                    String.join("\n", violations));
        }
        return violations;
    }
}
