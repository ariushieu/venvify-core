package com.venvify.venvifycore.wallet.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
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
