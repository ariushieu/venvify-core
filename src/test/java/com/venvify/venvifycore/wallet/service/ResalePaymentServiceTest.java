package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.PaymentProvider;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.TransactionRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResalePaymentServiceTest {

    @Mock
    private LedgerService ledgerService;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ResalePaymentService service;

    private User payer;
    private User payee;
    private Event event;
    private Wallet payerWallet;
    private Wallet payeeWallet;
    private Wallet commissionJar;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resaleCommissionPercent", 0);

        payer = user(2L);
        payee = user(1L);
        event = Event.builder().host(payee).title("Title").slug("title")
                .maxSlots(10).claimedSlots(1).priceAmount(100_000L).build();
        event.setId(100L);

        payerWallet = wallet(20L, WalletAccountType.USER);
        payeeWallet = wallet(10L, WalletAccountType.USER);
        commissionJar = wallet(4L, WalletAccountType.COMMISSION);

        lenient().when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(payerWallet));
        lenient().when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(payeeWallet));
        lenient().when(walletRepository.findByAccountType(WalletAccountType.COMMISSION))
                .thenReturn(Optional.of(commissionJar));
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static User user(long id) {
        User u = User.builder().email(id + "@venvify.com").fullName("U" + id)
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>()).build();
        u.setId(id);
        return u;
    }

    private static Wallet wallet(long id, WalletAccountType type) {
        Wallet w = Wallet.builder().accountType(type).balanceCached(0L).build();
        w.setId(id);
        return w;
    }

    @Test
    void pay_zeroCommission_fullPriceToPayee() {
        Transaction txn = service.payTicketResale(payer, payee, 80_000L, event);

        assertThat(txn.getType()).isEqualTo(TransactionType.TICKET_RESALE);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(txn.getAmount()).isEqualTo(80_000L);
        assertThat(txn.getTransactionRef()).startsWith("RSL-");
        assertThat(txn.getPaymentProvider()).isEqualTo(PaymentProvider.INTERNAL);
        assertThat(txn.getUser()).isSameAs(payer);
        assertThat(txn.getCompletedAt()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerService.Leg>> legs = ArgumentCaptor.forClass(List.class);
        verify(ledgerService).postSplit(eq(txn), eq(20L), eq(80_000L), legs.capture(), anyString());
        // O1 = 0%: full giá về sender, leg commission 0 (LedgerService tự skip khi ghi sổ).
        assertThat(legs.getValue()).containsExactly(
                new LedgerService.Leg(10L, 80_000L),
                new LedgerService.Leg(4L, 0L));
    }

    @Test
    void pay_withCommission_floorsLikePurchaseFlow() {
        // Bật phí qua config không sửa code: 10% của 99_999 → floor 9_999, net 90_000 (R17-style).
        ReflectionTestUtils.setField(service, "resaleCommissionPercent", 10);

        Transaction txn = service.payTicketResale(payer, payee, 99_999L, event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerService.Leg>> legs = ArgumentCaptor.forClass(List.class);
        verify(ledgerService).postSplit(eq(txn), eq(20L), eq(99_999L), legs.capture(), anyString());
        assertThat(legs.getValue()).containsExactly(
                new LedgerService.Leg(10L, 90_000L),
                new LedgerService.Leg(4L, 9_999L));
    }

    @Test
    void pay_nonPositivePrice_isABug() {
        assertThatThrownBy(() -> service.payTicketResale(payer, payee, 0L, event))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ledgerService, never()).postSplit(any(), anyLong(), anyLong(), any(), anyString());
    }
}
