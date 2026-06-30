package com.venvify.venvifycore.wallet.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "wallets",
        uniqueConstraints = @UniqueConstraint(name = "uq_wallet_user", columnNames = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet extends BaseEntity {

    /**
     * Loại tài khoản trong sổ kép (double-entry, D12). {@code USER} = ví user thật; các giá trị
     * còn lại là hũ hệ thống (ESCROW/COMMISSION/BANK_CLEARING/SUSPENSE) với {@link #user} = null.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private WalletAccountType accountType = WalletAccountType.USER;

    /** Chủ ví. NULL với hũ hệ thống. UNIQUE(user_id) vẫn đúng vì MySQL coi nhiều NULL là khác nhau. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    /**
     * Cache số dư (D2) — chỉ cập nhật trong CÙNG transaction với insert ledger.
     * Nguồn sự thật là SUM(ledger_entries.amount); có job reconcile so khớp định kỳ.
     */
    @Column(name = "balance_cached", nullable = false)
    @Builder.Default
    private Long balanceCached = 0L;
}
