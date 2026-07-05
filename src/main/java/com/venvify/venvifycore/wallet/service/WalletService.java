package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.repository.UserRepository;
import com.venvify.venvifycore.wallet.dto.LedgerEntryResponse;
import com.venvify.venvifycore.wallet.dto.WalletResponse;
import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.PaymentProvider;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.mapper.WalletMapper;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository;
import com.venvify.venvifycore.wallet.repository.TransactionRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * API ví (money-core §3.5–3.6): số dư, sao kê, và top-up dev-only để test paid flow
 * trước khi có Sepay. Money-in THẬT duy nhất ở prod là Sepay (slice sau).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final WalletMapper walletMapper;
    private final Environment environment;

    @Value("${app.money.dev-topup-enabled:false}")
    private boolean devTopupEnabled;

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String userPublicId) {
        return walletMapper.toResponse(requireWalletOf(userPublicId));
    }

    /** Sao kê phân trang, sort id DESC (R15 — không sort created_at, tie trong cùng micro giây). */
    @Transactional(readOnly = true)
    public PagedResponse<LedgerEntryResponse> listMyEntries(String userPublicId, Pageable pageable) {
        Wallet wallet = requireWalletOf(userPublicId);
        Page<LedgerEntry> page = ledgerEntryRepository.findByWalletIdOrderByIdDesc(wallet.getId(), safePageable(pageable));
        return PagedResponse.of(page.map(walletMapper::toEntryResponse));
    }

    /**
     * Top-up dev-only (§3.6, R16): cần flag {@code dev-topup-enabled} BẬT **và** profile khác
     * prod — prod luôn 404 bất kể flag, như thể endpoint không tồn tại. Không có đường in tiền
     * ở prod. Tiền đi BANK_CLEARING → ví user (đúng chiều money-in thật sau này của Sepay).
     */
    @Transactional
    public WalletResponse devTopup(String userPublicId, long amount) {
        if (!devTopupEnabled || environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ResourceNotFoundException("Not found");
        }

        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Wallet wallet = requireWallet(user);
        Wallet clearing = walletRepository.findByAccountType(WalletAccountType.BANK_CLEARING)
                .orElseThrow(() -> new IllegalStateException("BANK_CLEARING system wallet missing"));

        Transaction txn = transactionRepository.save(Transaction.builder()
                .type(TransactionType.TOPUP)
                .status(TransactionStatus.SUCCESS)
                .amount(amount)
                .transactionRef(TransactionRefs.next("TOP"))
                .paymentProvider(PaymentProvider.INTERNAL)
                .user(user)
                .completedAt(Instant.now())
                .build());

        ledgerService.postTransfer(txn, clearing.getId(), wallet.getId(), amount, "Dev top-up");
        log.info("Dev top-up: +{} VND into wallet {} (user {})", amount, wallet.getId(), user.getId());
        return walletMapper.toResponse(wallet);
    }

    // ----- helpers -----

    private Wallet requireWalletOf(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return requireWallet(user);
    }

    private Wallet requireWallet(User user) {
        // Đăng ký là có ví — thiếu là sự cố dữ liệu, không phải lỗi client.
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User " + user.getId() + " has no wallet"));
    }

    private static Pageable safePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "id");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
