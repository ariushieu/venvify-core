package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.repository.UserRepository;
import com.venvify.venvifycore.wallet.dto.LedgerEntryResponse;
import com.venvify.venvifycore.wallet.dto.WalletResponse;
import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.mapper.WalletMapper;
import com.venvify.venvifycore.wallet.repository.LedgerEntryRepository;
import com.venvify.venvifycore.wallet.repository.TransactionRepository;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private WalletMapper walletMapper;
    @Mock
    private Environment environment;

    @InjectMocks
    private WalletService walletService;

    private static final String USER_PID = "user-pid";
    private static final WalletResponse WALLET_RESPONSE = new WalletResponse("w-pid", 50_000L, "VND");

    private User user;
    private Wallet wallet;
    private Wallet clearingJar;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("user@venvify.com").fullName("User")
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        user.setId(7L);
        user.setPublicId(USER_PID);

        wallet = Wallet.builder().accountType(WalletAccountType.USER).balanceCached(50_000L).build();
        wallet.setId(70L);
        clearingJar = Wallet.builder().accountType(WalletAccountType.BANK_CLEARING).balanceCached(0L).build();
        clearingJar.setId(1L);

        lenient().when(userRepository.findByPublicId(USER_PID)).thenReturn(Optional.of(user));
        lenient().when(walletRepository.findByUserId(7L)).thenReturn(Optional.of(wallet));
    }

    // ---- getMyWallet / listMyEntries ----

    @Test
    void getMyWallet_returnsMappedBalance() {
        when(walletMapper.toResponse(wallet)).thenReturn(WALLET_RESPONSE);

        assertThat(walletService.getMyWallet(USER_PID)).isSameAs(WALLET_RESPONSE);
    }

    @Test
    void listMyEntries_sortsByIdDesc() {
        LedgerEntry entry = LedgerEntry.builder().wallet(wallet).amount(-1_000L).balanceAfter(49_000L).build();
        LedgerEntryResponse mapped = new LedgerEntryResponse(
                "le-pid", -1_000L, 49_000L, "x", TransactionType.TICKET_PURCHASE, "TKT-1", Instant.now());
        // R15: sao kê PHẢI đi qua query sort theo id, không phải created_at.
        when(ledgerEntryRepository.findByWalletIdOrderByIdDesc(eq(70L), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));
        when(walletMapper.toEntryResponse(entry)).thenReturn(mapped);

        PagedResponse<LedgerEntryResponse> result =
                walletService.listMyEntries(USER_PID, PageRequest.of(0, 20));

        assertThat(result.items()).containsExactly(mapped);
    }

    // ---- devTopup (R16 double-gate) ----

    @Test
    void devTopup_flagDisabled_returns404WithoutMovingMoney() {
        ReflectionTestUtils.setField(walletService, "devTopupEnabled", false);

        assertThatThrownBy(() -> walletService.devTopup(USER_PID, 100_000L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).save(any());
        verify(ledgerService, never()).postTransfer(any(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void devTopup_prodProfile_returns404EvenWithFlagOn() {
        ReflectionTestUtils.setField(walletService, "devTopupEnabled", true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        // R16: guard cứng — không tin mỗi flag, prod không bao giờ có đường in tiền.
        assertThatThrownBy(() -> walletService.devTopup(USER_PID, 100_000L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void devTopup_enabledOutsideProd_movesMoneyFromClearingJar() {
        ReflectionTestUtils.setField(walletService, "devTopupEnabled", true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(walletRepository.findByAccountType(WalletAccountType.BANK_CLEARING))
                .thenReturn(Optional.of(clearingJar));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletMapper.toResponse(wallet)).thenReturn(WALLET_RESPONSE);

        WalletResponse result = walletService.devTopup(USER_PID, 100_000L);

        assertThat(result).isSameAs(WALLET_RESPONSE);

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        Transaction txn = txnCaptor.getValue();
        assertThat(txn.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(txn.getTransactionRef()).startsWith("TOP-");
        assertThat(txn.getCompletedAt()).isNotNull();

        // Đúng chiều money-in thật của Sepay sau này: BANK_CLEARING → ví user.
        verify(ledgerService).postTransfer(eq(txn), eq(1L), eq(70L), eq(100_000L), anyString());
    }
}
