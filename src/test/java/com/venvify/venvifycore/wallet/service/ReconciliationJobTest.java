package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.email.EmailService;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.EscrowHoldRepository;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private EscrowHoldRepository escrowHoldRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReconciliationJob job;

    private Wallet userWallet;
    private Wallet clearingJar;
    private Wallet escrowJar;

    @BeforeEach
    void setUp() {
        userWallet = wallet(10L, WalletAccountType.USER, 40_000L);
        clearingJar = wallet(1L, WalletAccountType.BANK_CLEARING, -100_000L);
        escrowJar = wallet(2L, WalletAccountType.ESCROW, 60_000L);

        // Trạng thái SẠCH: user nạp 100k (clearing −100k), mua vé 60k đang nằm escrow (1 hold HELD).
        // lenient vì từng test override lại đúng stub mình cần làm lệch (strict stubs báo unused).
        lenient().when(ledgerEntryRepository.sumAll()).thenReturn(0L);
        lenient().when(ledgerEntryRepository.sumGroupByWallet()).thenReturn(List.of(
                walletSum(10L, 40_000L), walletSum(1L, -100_000L), walletSum(2L, 60_000L)));
        lenient().when(ledgerEntryRepository.findUnbalancedTransactions()).thenReturn(List.of());
        lenient().when(walletRepository.findAll())
                .thenReturn(List.of(userWallet, clearingJar, escrowJar));
        lenient().when(walletRepository.findByAccountType(WalletAccountType.ESCROW))
                .thenReturn(Optional.of(escrowJar));
        lenient().when(escrowHoldRepository.sumGrossByStatus(EscrowStatus.HELD)).thenReturn(60_000L);
    }

    private static Wallet wallet(long id, WalletAccountType type, long balance) {
        Wallet w = Wallet.builder().accountType(type).balanceCached(balance).build();
        w.setId(id);
        return w;
    }

    private static LedgerEntryRepository.WalletSum walletSum(long walletId, long total) {
        return new LedgerEntryRepository.WalletSum() {
            @Override
            public Long getWalletId() {
                return walletId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private static LedgerEntryRepository.TransactionSum txnSum(long txnId, long total) {
        return new LedgerEntryRepository.TransactionSum() {
            @Override
            public Long getTransactionId() {
                return txnId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    @Test
    void reconcile_cleanBooks_reportsNothingAndSendsNoAlert() {
        List<String> violations = job.reconcile();

        assertThat(violations).isEmpty();
        verify(emailService, never()).sendOpsAlert(anyString(), anyString());
    }

    @Test
    void reconcile_grandTotalNonZero_isP0AndEmailsAdmin() {
        when(ledgerEntryRepository.sumAll()).thenReturn(7L);

        List<String> violations = job.reconcile();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("grand total");
        verify(emailService).sendOpsAlert(contains("[P0]"), contains("grand total"));
    }

    @Test
    void reconcile_cachedBalanceDriftsFromLedger_isDetectedPerWallet() {
        userWallet.setBalanceCached(39_999L); // lệch 1 đồng cũng phải bắt

        List<String> violations = job.reconcile();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("Wallet 10").contains("39999").contains("40000");
        verify(emailService).sendOpsAlert(contains("[P0]"), anyString());
    }

    @Test
    void reconcile_walletWithoutEntries_mustHaveZeroBalance() {
        Wallet ghost = wallet(99L, WalletAccountType.USER, 5_000L); // có tiền mà không có bút toán nào
        when(walletRepository.findAll())
                .thenReturn(List.of(userWallet, clearingJar, escrowJar, ghost));

        List<String> violations = job.reconcile();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("Wallet 99");
    }

    @Test
    void reconcile_unbalancedTransaction_isReported() {
        when(ledgerEntryRepository.findUnbalancedTransactions()).thenReturn(List.of(txnSum(42L, 500L)));

        List<String> violations = job.reconcile();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("Transaction 42").contains("500");
        verify(emailService).sendOpsAlert(contains("[P0]"), anyString());
    }

    @Test
    void reconcile_escrowJarNotCoveringHeldGross_isReported() {
        when(escrowHoldRepository.sumGrossByStatus(EscrowStatus.HELD)).thenReturn(90_000L);

        List<String> violations = job.reconcile();

        // Bất biến 4 lệch; các bất biến khác vẫn sạch trong fixture này.
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("ESCROW jar");
        verify(emailService).sendOpsAlert(contains("[P0]"), contains("ESCROW"));
    }

    @Test
    void reconcile_multipleViolations_sendsSingleAggregatedAlert() {
        when(ledgerEntryRepository.sumAll()).thenReturn(7L);
        when(ledgerEntryRepository.findUnbalancedTransactions()).thenReturn(List.of(txnSum(42L, 500L)));

        List<String> violations = job.reconcile();

        assertThat(violations).hasSize(2);
        // 1 email gộp mọi vi phạm, không spam từng cái.
        verify(emailService).sendOpsAlert(contains("2 violation(s)"), anyString());
    }
}
