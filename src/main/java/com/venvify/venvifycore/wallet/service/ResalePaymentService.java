package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.PaymentProvider;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
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
 * Thanh toán resale vé P2P (plan P3 §1.3, đường ví): ví receiver → [ví sender: net,
 * hũ COMMISSION: phí]. Escrow vé gốc KHÔNG đụng (D10) — host vẫn nhận đúng 1 lần/chỗ.
 * O1: phí resale 0% MVP nhưng code đi {@code postSplit} sẵn — bật phí sau chỉ đổi config,
 * leg 0 đồng LedgerService tự bỏ qua.
 *
 * <p>{@code MANDATORY}: chạy trong transaction accept của TransferService (R18);
 * caller PHẢI đang giữ khóa event + booking (R13: event → booking → ví).
 */
@Service
@RequiredArgsConstructor
public class ResalePaymentService {

    private final LedgerService ledgerService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    /** O1 — 0% MVP (khuyến khích pass đúng giá); đổi qua config không sửa code. */
    @Value("${app.money.resale-commission-percent:0}")
    private int resaleCommissionPercent;

    /**
     * Trừ ví payer (thiếu tiền → BadRequest từ LedgerService → rollback cả accept),
     * ghi txn TICKET_RESALE SUCCESS gắn payer để lịch sử ví 2 bên đều soi ra được.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction payTicketResale(User payer, User payee, long price, Event event) {
        if (price <= 0) {
            throw new IllegalArgumentException("Resale price must be positive, got " + price);
        }

        Transaction txn = transactionRepository.save(Transaction.builder()
                .type(TransactionType.TICKET_RESALE)
                .status(TransactionStatus.SUCCESS)
                .amount(price)
                .transactionRef(TransactionRefs.next("RSL"))
                .paymentProvider(PaymentProvider.INTERNAL)
                .user(payer)
                .event(event)
                .completedAt(Instant.now())
                .build());

        long commission = resaleCommissionPercent > 0
                ? Math.floorDiv(price * resaleCommissionPercent, 100)
                : 0L;
        ledgerService.postSplit(txn, requireUserWallet(payer).getId(), price,
                List.of(new LedgerService.Leg(requireUserWallet(payee).getId(), price - commission),
                        new LedgerService.Leg(systemJar(WalletAccountType.COMMISSION).getId(), commission)),
                "Ticket resale: " + event.getTitle());
        return txn;
    }

    // ----- helpers (pattern EscrowService) -----

    private Wallet requireUserWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User " + user.getId() + " has no wallet"));
    }

    private Wallet systemJar(WalletAccountType type) {
        return walletRepository.findByAccountType(type)
                .orElseThrow(() -> new IllegalStateException("System wallet missing: " + type));
    }
}
