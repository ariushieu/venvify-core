package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.booking.repository.BookingRepository;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.wallet.entity.EscrowHold;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import com.venvify.venvifycore.wallet.enums.PaymentProvider;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.EscrowHoldRepository;
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

import java.time.Instant;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscrowServiceTest {

    @Mock
    private LedgerService ledgerService;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private EscrowHoldRepository escrowHoldRepository;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private EscrowService escrowService;

    private User host;
    private User buyer;
    private Event event;
    private Booking booking;
    private Wallet buyerWallet;
    private Wallet hostWallet;
    private Wallet escrowJar;
    private Wallet commissionJar;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(escrowService, "commissionPercent", 5);

        host = user(1L, "host-pid");
        buyer = user(2L, "buyer-pid");
        event = Event.builder()
                .host(host).title("Title").slug("title")
                .maxSlots(10).claimedSlots(1).priceAmount(99_999L)
                .status(EventStatus.PUBLISHED)
                .build();
        event.setId(100L);
        booking = Booking.builder()
                .event(event).attendee(buyer)
                .status(BookingStatus.CONFIRMED).pricePaid(99_999L).bookedAt(Instant.now())
                .build();
        booking.setId(500L);

        buyerWallet = wallet(20L, WalletAccountType.USER);
        hostWallet = wallet(10L, WalletAccountType.USER);
        escrowJar = wallet(3L, WalletAccountType.ESCROW);
        commissionJar = wallet(4L, WalletAccountType.COMMISSION);

        lenient().when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(buyerWallet));
        lenient().when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(hostWallet));
        lenient().when(walletRepository.findByAccountType(WalletAccountType.ESCROW))
                .thenReturn(Optional.of(escrowJar));
        lenient().when(walletRepository.findByAccountType(WalletAccountType.COMMISSION))
                .thenReturn(Optional.of(commissionJar));
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(escrowHoldRepository.save(any(EscrowHold.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(long id, String publicId) {
        User u = User.builder()
                .email(publicId + "@venvify.com").fullName("User")
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        u.setId(id);
        u.setPublicId(publicId);
        return u;
    }

    private Wallet wallet(long id, WalletAccountType type) {
        Wallet w = Wallet.builder().accountType(type).balanceCached(0L).build();
        w.setId(id);
        return w;
    }

    private EscrowHold heldHold() {
        EscrowHold hold = EscrowHold.builder()
                .event(event).booking(booking)
                .grossAmount(99_999L).commissionAmount(4_999L).hostNetAmount(95_000L)
                .status(EscrowStatus.HELD).heldAt(Instant.now())
                .build();
        hold.setId(700L);
        return hold;
    }

    // ---- holdTicketPayment (§3.1) ----

    @Test
    void holdTicketPayment_movesMoneyToEscrowAndCreatesHoldWithFlooredCommission() {
        when(escrowHoldRepository.findByBookingIdAndStatus(500L, EscrowStatus.HELD))
                .thenReturn(Optional.empty());

        Transaction txn = escrowService.holdTicketPayment(booking, buyer, 99_999L);

        assertThat(txn.getType()).isEqualTo(TransactionType.TICKET_PURCHASE);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(txn.getAmount()).isEqualTo(99_999L);
        assertThat(txn.getTransactionRef()).startsWith("TKT-");
        assertThat(txn.getPaymentProvider()).isEqualTo(PaymentProvider.INTERNAL);
        assertThat(txn.getUser()).isSameAs(buyer);
        assertThat(txn.getEvent()).isSameAs(event);
        assertThat(txn.getCompletedAt()).isNotNull();

        verify(ledgerService).postTransfer(eq(txn), eq(20L), eq(3L), eq(99_999L), anyString());

        ArgumentCaptor<EscrowHold> captor = ArgumentCaptor.forClass(EscrowHold.class);
        verify(escrowHoldRepository).save(captor.capture());
        EscrowHold hold = captor.getValue();
        // R17: commission = floor(99.999 × 5%) = 4.999; host_net = 95.000; tổng khớp gross.
        assertThat(hold.getGrossAmount()).isEqualTo(99_999L);
        assertThat(hold.getCommissionAmount()).isEqualTo(4_999L);
        assertThat(hold.getHostNetAmount()).isEqualTo(95_000L);
        assertThat(hold.getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(hold.getHeldAt()).isNotNull();
        assertThat(hold.getBooking()).isSameAs(booking);
        assertThat(hold.getEvent()).isSameAs(event);
    }

    @Test
    void holdTicketPayment_duplicateActiveHold_throwsConflict() {
        when(escrowHoldRepository.findByBookingIdAndStatus(500L, EscrowStatus.HELD))
                .thenReturn(Optional.of(heldHold()));

        // F6: mỗi booking chỉ 1 hold HELD.
        assertThatThrownBy(() -> escrowService.holdTicketPayment(booking, buyer, 99_999L))
                .isInstanceOf(ConflictException.class);
        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).postTransfer(any(), anyLong(), anyLong(), anyLong(), anyString());
    }

    // ---- refundHeldForEvent (§3.3) ----

    @Test
    void refundHeldForEvent_refundsFullGrossPerHoldAndMarksBookingRefunded() {
        EscrowHold hold = heldHold();
        when(escrowHoldRepository.findByEventIdAndStatus(100L, EscrowStatus.HELD))
                .thenReturn(List.of(hold));

        escrowService.refundHeldForEvent(event);

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        Transaction txn = txnCaptor.getValue();
        assertThat(txn.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(txn.getTransactionRef()).startsWith("RFD-");
        assertThat(txn.getUser()).isSameAs(buyer);
        // Hoàn 100% GROSS — commission chưa thực thu khi tiền còn trong escrow.
        assertThat(txn.getAmount()).isEqualTo(99_999L);

        verify(ledgerService).postTransfer(eq(txn), eq(3L), eq(20L), eq(99_999L), anyString());

        assertThat(hold.getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(hold.getRefundedAt()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REFUNDED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void refundHeldForEvent_noHolds_movesNoMoney() {
        when(escrowHoldRepository.findByEventIdAndStatus(100L, EscrowStatus.HELD))
                .thenReturn(List.of());

        escrowService.refundHeldForEvent(event);

        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).postTransfer(any(), anyLong(), anyLong(), anyLong(), anyString());
    }

    // ---- releaseHold (§3.4) ----

    @Test
    void releaseHold_splitsGrossBetweenHostAndCommissionJar() {
        EscrowHold hold = heldHold();
        when(escrowHoldRepository.findById(700L)).thenReturn(Optional.of(hold));

        escrowService.releaseHold(700L);

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        Transaction txn = txnCaptor.getValue();
        assertThat(txn.getType()).isEqualTo(TransactionType.COMMISSION);
        assertThat(txn.getTransactionRef()).startsWith("REL-");
        assertThat(txn.getUser()).isSameAs(host);

        verify(ledgerService).postSplit(eq(txn), eq(3L), eq(99_999L),
                eq(List.of(new LedgerService.Leg(10L, 95_000L), new LedgerService.Leg(4L, 4_999L))),
                anyString());

        assertThat(hold.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(hold.getReleasedAt()).isNotNull();
    }

    @Test
    void releaseHold_alreadyProcessedHold_isIdempotentNoOp() {
        EscrowHold hold = heldHold();
        hold.setStatus(EscrowStatus.REFUNDED); // event bị hủy trong dispute window
        when(escrowHoldRepository.findById(700L)).thenReturn(Optional.of(hold));

        escrowService.releaseHold(700L);

        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).postSplit(any(), anyLong(), anyLong(), any(), anyString());
        assertThat(hold.getStatus()).isEqualTo(EscrowStatus.REFUNDED);
    }
}
