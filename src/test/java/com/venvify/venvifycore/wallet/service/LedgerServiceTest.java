package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    private final Transaction txn = Transaction.builder().transactionRef("TKT-test").build();

    private Wallet wallet(long id, WalletAccountType type, long balance) {
        Wallet w = Wallet.builder().accountType(type).balanceCached(balance).build();
        w.setId(id);
        return w;
    }

    private void stubLock(Wallet... wallets) {
        for (Wallet w : wallets) {
            lenient().when(walletRepository.findByIdForUpdate(w.getId())).thenReturn(Optional.of(w));
        }
        lenient().when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private List<LedgerEntry> savedEntries() {
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    // ---- postTransfer ----

    @Test
    void postTransfer_happyPath_writesBalancedPairAndUpdatesBalances() {
        Wallet from = wallet(5L, WalletAccountType.USER, 100_000L);
        Wallet to = wallet(9L, WalletAccountType.ESCROW, 0L);
        stubLock(from, to);

        ledgerService.postTransfer(txn, 5L, 9L, 60_000L, "ticket");

        assertThat(from.getBalanceCached()).isEqualTo(40_000L);
        assertThat(to.getBalanceCached()).isEqualTo(60_000L);

        List<LedgerEntry> entries = savedEntries();
        assertThat(entries).hasSize(2);
        LedgerEntry debit = entries.get(0);
        assertThat(debit.getWallet()).isSameAs(from);
        assertThat(debit.getAmount()).isEqualTo(-60_000L);
        assertThat(debit.getBalanceAfter()).isEqualTo(40_000L);
        assertThat(debit.getTransaction()).isSameAs(txn);
        LedgerEntry credit = entries.get(1);
        assertThat(credit.getWallet()).isSameAs(to);
        assertThat(credit.getAmount()).isEqualTo(60_000L);
        assertThat(credit.getBalanceAfter()).isEqualTo(60_000L);
        // R8: cặp bút toán cân — tổng = 0.
        assertThat(entries.stream().mapToLong(LedgerEntry::getAmount).sum()).isZero();
    }

    @Test
    void postTransfer_insufficientUserBalance_throwsBadRequestAndWritesNothing() {
        Wallet from = wallet(5L, WalletAccountType.USER, 10_000L);
        Wallet to = wallet(9L, WalletAccountType.ESCROW, 0L);
        stubLock(from, to);

        assertThatThrownBy(() -> ledgerService.postTransfer(txn, 5L, 9L, 60_000L, "ticket"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient");
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void postTransfer_systemJarMayGoNegative() {
        // BANK_CLEARING là gương của bank thật — âm là hợp lệ (R14 chỉ guard ví USER).
        Wallet clearing = wallet(3L, WalletAccountType.BANK_CLEARING, 0L);
        Wallet user = wallet(7L, WalletAccountType.USER, 0L);
        stubLock(clearing, user);

        ledgerService.postTransfer(txn, 3L, 7L, 50_000L, "topup");

        assertThat(clearing.getBalanceCached()).isEqualTo(-50_000L);
        assertThat(user.getBalanceCached()).isEqualTo(50_000L);
    }

    @Test
    void postTransfer_locksWalletsInAscendingIdOrder() {
        // from có id LỚN hơn to → thứ tự khóa vẫn phải là id tăng dần (R13), không theo chiều tiền.
        Wallet from = wallet(20L, WalletAccountType.USER, 100_000L);
        Wallet to = wallet(4L, WalletAccountType.ESCROW, 0L);
        stubLock(from, to);

        ledgerService.postTransfer(txn, 20L, 4L, 1_000L, "ticket");

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(4L);
        order.verify(walletRepository).findByIdForUpdate(20L);
    }

    @Test
    void postTransfer_nonPositiveAmount_throws() {
        assertThatThrownBy(() -> ledgerService.postTransfer(txn, 1L, 2L, 0L, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledgerService.postTransfer(txn, 1L, 2L, -5L, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(walletRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void postTransfer_sameWallet_throws() {
        assertThatThrownBy(() -> ledgerService.postTransfer(txn, 1L, 1L, 5L, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(walletRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void postTransfer_missingWallet_throwsIllegalState() {
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.postTransfer(txn, 1L, 2L, 5L, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wallet not found");
    }

    // ---- postSplit ----

    @Test
    void postSplit_commissionRounding_writesExactSplit() {
        // R17: gross 99.999đ, rate 5% → commission floor = 4.999đ, host_net = 95.000đ.
        Wallet escrow = wallet(2L, WalletAccountType.ESCROW, 99_999L);
        Wallet host = wallet(11L, WalletAccountType.USER, 0L);
        Wallet commission = wallet(3L, WalletAccountType.COMMISSION, 0L);
        stubLock(escrow, host, commission);

        ledgerService.postSplit(txn, 2L, 99_999L,
                List.of(new LedgerService.Leg(11L, 95_000L), new LedgerService.Leg(3L, 4_999L)),
                "release");

        assertThat(escrow.getBalanceCached()).isZero();
        assertThat(host.getBalanceCached()).isEqualTo(95_000L);
        assertThat(commission.getBalanceCached()).isEqualTo(4_999L);

        List<LedgerEntry> entries = savedEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.stream().mapToLong(LedgerEntry::getAmount).sum()).isZero();
    }

    @Test
    void postSplit_legsSumMismatch_throwsBeforeWriting() {
        assertThatThrownBy(() -> ledgerService.postSplit(txn, 2L, 100_000L,
                List.of(new LedgerService.Leg(11L, 95_000L), new LedgerService.Leg(3L, 4_999L)),
                "release"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("!= gross");
        // Validate trước khi khóa/ghi — sổ không bao giờ thấy split lệch.
        verify(walletRepository, never()).findByIdForUpdate(anyLong());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void postSplit_zeroLeg_isSkippedNotWritten() {
        // Commission 0đ (gross quá nhỏ) → không ghi bút toán 0 (CHECK amount <> 0).
        Wallet escrow = wallet(2L, WalletAccountType.ESCROW, 10L);
        Wallet host = wallet(11L, WalletAccountType.USER, 0L);
        Wallet commission = wallet(3L, WalletAccountType.COMMISSION, 0L);
        stubLock(escrow, host, commission);

        ledgerService.postSplit(txn, 2L, 10L,
                List.of(new LedgerService.Leg(11L, 10L), new LedgerService.Leg(3L, 0L)),
                "release");

        List<LedgerEntry> entries = savedEntries();
        assertThat(entries).hasSize(2);
        assertThat(entries).noneMatch(e -> e.getAmount() == 0L);
        assertThat(commission.getBalanceCached()).isZero();
    }

    @Test
    void postSplit_locksAllWalletsAscending() {
        Wallet escrow = wallet(9L, WalletAccountType.ESCROW, 1_000L);
        Wallet host = wallet(1L, WalletAccountType.USER, 0L);
        Wallet commission = wallet(4L, WalletAccountType.COMMISSION, 0L);
        stubLock(escrow, host, commission);

        ledgerService.postSplit(txn, 9L, 1_000L,
                List.of(new LedgerService.Leg(1L, 950L), new LedgerService.Leg(4L, 50L)),
                "release");

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(1L);
        order.verify(walletRepository).findByIdForUpdate(4L);
        order.verify(walletRepository).findByIdForUpdate(9L);
    }

    @Test
    void postSplit_legTargetingSource_throws() {
        assertThatThrownBy(() -> ledgerService.postSplit(txn, 2L, 100L,
                List.of(new LedgerService.Leg(2L, 100L)), "release"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void postSplit_emptyLegs_throws() {
        assertThatThrownBy(() -> ledgerService.postSplit(txn, 2L, 100L, List.of(), "release"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
