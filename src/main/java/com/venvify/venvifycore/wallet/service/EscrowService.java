package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.booking.repository.BookingRepository;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.user.entity.User;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Nghiệp vụ escrow quanh tiền vé (money-core plan §3): giữ tiền lúc mua (HELD), hoàn khi event
 * bị hủy (REFUNDED), chia cho host + hũ commission khi release (RELEASED).
 *
 * <p>Mọi method đều là participant ({@code MANDATORY}) — transaction do use-case sở hữu
 * (BookingService/EventService/job), đúng R18: 1 use-case = 1 {@code @Transactional}.
 * Caller PHẢI đang giữ khóa event (R13: event trước → ví sau).
 */
@Service
@RequiredArgsConstructor
public class EscrowService {

    private final LedgerService ledgerService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final EscrowHoldRepository escrowHoldRepository;
    private final BookingRepository bookingRepository;

    /** O-M1 — 5%, đổi qua config không cần migration. */
    @Value("${app.money.ticket-commission-percent:5}")
    private int commissionPercent;

    /**
     * Mua vé bằng ví (§3.1, chạy trong tx của BookingService.create): trừ ví buyer → hũ ESCROW
     * + tạo hold HELD. Thiếu tiền → BadRequest từ LedgerService → rollback cả slot đã tăng.
     *
     * @return transaction mua vé để booking ghi lại (purchase_txn).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction holdTicketPayment(Booking booking, User buyer, long price) {
        // F6: mỗi booking chỉ được có đúng 1 hold HELD (hold REFUNDED cũ sau re-book là hợp lệ).
        escrowHoldRepository.findByBookingIdAndStatus(booking.getId(), EscrowStatus.HELD)
                .ifPresent(h -> {
                    throw new ConflictException("Booking already has an active escrow hold");
                });

        Event event = booking.getEvent();
        Transaction txn = transactionRepository.save(Transaction.builder()
                .type(TransactionType.TICKET_PURCHASE)
                .status(TransactionStatus.SUCCESS)
                .amount(price)
                .transactionRef(ref("TKT"))
                .paymentProvider(PaymentProvider.INTERNAL)
                .user(buyer)
                .event(event)
                .completedAt(Instant.now())
                .build());

        ledgerService.postTransfer(txn, requireUserWallet(buyer).getId(), systemJar(WalletAccountType.ESCROW).getId(),
                price, "Ticket purchase: " + event.getTitle());

        long commission = commissionPercent > 0 ? Math.floorDiv(price * commissionPercent, 100) : 0L;
        escrowHoldRepository.save(EscrowHold.builder()
                .event(event)
                .booking(booking)
                .grossAmount(price)
                .commissionAmount(commission)
                .hostNetAmount(price - commission)  // R17: tổng luôn khớp gross
                .status(EscrowStatus.HELD)
                .heldAt(Instant.now())
                .build());
        return txn;
    }

    /**
     * Refund toàn bộ hold HELD của event bị hủy (§3.3 — luồng refund DUY NHẤT). Hoàn 100% gross
     * về buyer: commission chỉ thực thu khi release, tiền vẫn nằm nguyên trong ESCROW.
     * Booking → REFUNDED (phân biệt CANCELLED của vé free).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundHeldForEvent(Event event) {
        List<EscrowHold> holds = escrowHoldRepository.findByEventIdAndStatus(event.getId(), EscrowStatus.HELD);
        Wallet escrowJar = holds.isEmpty() ? null : systemJar(WalletAccountType.ESCROW);
        for (EscrowHold hold : holds) {
            Booking booking = hold.getBooking();
            User buyer = booking.getAttendee();

            Transaction txn = transactionRepository.save(Transaction.builder()
                    .type(TransactionType.REFUND)
                    .status(TransactionStatus.SUCCESS)
                    .amount(hold.getGrossAmount())
                    .transactionRef(ref("RFD"))
                    .paymentProvider(PaymentProvider.INTERNAL)
                    .user(buyer)
                    .event(event)
                    .completedAt(Instant.now())
                    .build());

            ledgerService.postTransfer(txn, escrowJar.getId(), requireUserWallet(buyer).getId(),
                    hold.getGrossAmount(), "Refund — event cancelled: " + event.getTitle());

            hold.setStatus(EscrowStatus.REFUNDED);
            hold.setRefundedAt(Instant.now());
            escrowHoldRepository.save(hold);

            booking.setStatus(BookingStatus.REFUNDED);
            bookingRepository.save(booking);
        }
    }

    /**
     * Release 1 hold đủ điều kiện (event ENDED quá dispute window): ESCROW → [ví HOST: host_net,
     * hũ COMMISSION: commission]. Job §3.4 gọi theo id — method TỰ mở transaction (ngoại lệ có
     * chủ đích so với MANDATORY: use-case ở đây chính là "release 1 hold", mỗi hold 1 tx riêng
     * để 1 hold lỗi không chặn hold khác). Re-check status trong tx → idempotent, không release
     * đôi và không đụng hold vừa bị refund bởi tx khác (chốt cuối là optimistic @Version).
     */
    @Transactional
    public void releaseHold(Long holdId) {
        EscrowHold hold = escrowHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalStateException("Escrow hold not found: " + holdId));
        if (hold.getStatus() != EscrowStatus.HELD) {
            return; // đã refund/release ở transaction khác
        }
        Event event = hold.getEvent();
        User host = event.getHost();

        Transaction txn = transactionRepository.save(Transaction.builder()
                .type(TransactionType.COMMISSION)
                .status(TransactionStatus.SUCCESS)
                .amount(hold.getGrossAmount())
                .transactionRef(ref("REL"))
                .paymentProvider(PaymentProvider.INTERNAL)
                .user(host)
                .event(event)
                .completedAt(Instant.now())
                .build());

        ledgerService.postSplit(txn, systemJar(WalletAccountType.ESCROW).getId(), hold.getGrossAmount(),
                List.of(new LedgerService.Leg(requireUserWallet(host).getId(), hold.getHostNetAmount()),
                        new LedgerService.Leg(systemJar(WalletAccountType.COMMISSION).getId(), hold.getCommissionAmount())),
                "Escrow release: " + event.getTitle());

        hold.setStatus(EscrowStatus.RELEASED);
        hold.setReleasedAt(Instant.now());
        escrowHoldRepository.save(hold);
    }

    // ----- helpers -----

    private Wallet requireUserWallet(User user) {
        // Không xảy ra với user thật (đăng ký là có ví) — nổ IllegalState thay vì 404 user-facing.
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User " + user.getId() + " has no wallet"));
    }

    private Wallet systemJar(WalletAccountType type) {
        return walletRepository.findByAccountType(type)
                .orElseThrow(() -> new IllegalStateException("System wallet missing: " + type));
    }

    private static String ref(String prefix) {
        return TransactionRefs.next(prefix);
    }
}
