package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine sổ kép (money-core plan §2) — MỘT cửa duy nhất cho mọi chuyển động tiền.
 * Không service nào khác được tự insert ledger_entries hay sửa balance_cached.
 *
 * <p>Bất biến giữ ở đây:
 * <ul>
 *   <li>R13 — khóa ví theo id TĂNG DẦN (caller đã khóa event trước nếu có).</li>
 *   <li>R14 — ví USER không âm; hũ hệ thống ĐƯỢC âm (BANK_CLEARING là gương của bank).</li>
 *   <li>R8 — tổng bút toán của một transaction luôn = 0, assert trước khi trả về.</li>
 *   <li>R18 — participant: chạy TRONG transaction của caller, không tự mở
 *       ({@code MANDATORY} — gọi ngoài transaction là bug, fail ngay).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /** Một chân đích của {@link #postSplit}: cộng {@code amount} vào ví {@code toWalletId}. */
    public record Leg(Long toWalletId, long amount) {
    }

    /**
     * Chuyển {@code amount} từ ví này sang ví kia — cặp bút toán debit −amount / credit +amount,
     * cập nhật balance_cached cả 2 ví trong cùng transaction (D2).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postTransfer(Transaction txn, Long fromWalletId, Long toWalletId,
                             long amount, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive, got " + amount);
        }
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer within the same wallet");
        }

        Map<Long, Wallet> wallets = lockAscending(List.of(fromWalletId, toWalletId));
        Wallet from = wallets.get(fromWalletId);
        Wallet to = wallets.get(toWalletId);

        List<LedgerEntry> entries = new ArrayList<>();
        entries.add(post(from, txn, -amount, description));
        entries.add(post(to, txn, amount, description));
        assertBalanced(entries, txn);
    }

    /**
     * 1 nguồn −gross → N đích (release escrow: ví HOST + hũ COMMISSION). Tổng leg phải bằng
     * đúng gross (R17 — validate TRƯỚC khi ghi); leg 0 đồng được bỏ qua (bút toán 0 vô nghĩa
     * và vi phạm CHECK amount <> 0).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postSplit(Transaction txn, Long fromWalletId, long gross,
                          List<Leg> legs, String description) {
        if (gross <= 0) {
            throw new IllegalArgumentException("Split gross must be positive, got " + gross);
        }
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("Split needs at least one leg");
        }
        long legSum = 0;
        for (Leg leg : legs) {
            if (leg.amount() < 0) {
                throw new IllegalArgumentException("Split leg amount must not be negative");
            }
            if (leg.toWalletId().equals(fromWalletId)) {
                throw new IllegalArgumentException("Split leg cannot target the source wallet");
            }
            legSum += leg.amount();
        }
        if (legSum != gross) {
            // Lệch một xu cũng là bug chia tiền — nổ để rollback, không được ghi sổ lệch.
            throw new IllegalStateException(
                    "Split legs sum " + legSum + " != gross " + gross + " (txn " + txn.getTransactionRef() + ")");
        }

        List<Long> ids = new ArrayList<>();
        ids.add(fromWalletId);
        legs.forEach(leg -> ids.add(leg.toWalletId()));
        Map<Long, Wallet> wallets = lockAscending(ids);

        List<LedgerEntry> entries = new ArrayList<>();
        entries.add(post(wallets.get(fromWalletId), txn, -gross, description));
        for (Leg leg : legs) {
            if (leg.amount() == 0) {
                continue;
            }
            entries.add(post(wallets.get(leg.toWalletId()), txn, leg.amount(), description));
        }
        assertBalanced(entries, txn);
    }

    // ----- helpers -----

    /** Khóa các ví theo id tăng dần (R13 — chống deadlock), trả map id → ví đã khóa. */
    private Map<Long, Wallet> lockAscending(Collection<Long> walletIds) {
        Map<Long, Wallet> locked = new LinkedHashMap<>();
        walletIds.stream().distinct().sorted().forEach(id ->
                locked.put(id, walletRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalStateException("Wallet not found: " + id))));
        return locked;
    }

    /** Ghi 1 bút toán + cập nhật balance_cached của ví (đã khóa) trong cùng transaction (D2). */
    private LedgerEntry post(Wallet wallet, Transaction txn, long amount, String description) {
        long newBalance = wallet.getBalanceCached() + amount;
        if (newBalance < 0 && wallet.getAccountType() == WalletAccountType.USER) {
            // R14 — user-facing: ví không đủ tiền. Hũ hệ thống được âm nên không guard.
            throw new BadRequestException("Insufficient wallet balance");
        }
        wallet.setBalanceCached(newBalance);
        walletRepository.save(wallet);
        return ledgerEntryRepository.save(LedgerEntry.builder()
                .wallet(wallet)
                .transaction(txn)
                .amount(amount)
                .balanceAfter(newBalance)
                .description(description)
                .build());
    }

    /** R8 defensive: tổng bút toán vừa ghi của txn phải = 0; lệch → exception → rollback tất cả. */
    private void assertBalanced(List<LedgerEntry> entries, Transaction txn) {
        long sum = entries.stream().mapToLong(LedgerEntry::getAmount).sum();
        if (sum != 0) {
            throw new IllegalStateException(
                    "Ledger entries of txn " + txn.getTransactionRef() + " sum to " + sum + ", expected 0");
        }
    }
}
