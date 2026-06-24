package com.venvify.venvifycore.wallet.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bút toán APPEND-ONLY (SPEC §5.6). KHÔNG được UPDATE/DELETE — sửa sai bằng bút toán đảo.
 * Số dư thật của ví = SUM(amount) theo wallet.
 */
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_wallet", columnList = "wallet_id, created_at"),
        @Index(name = "idx_ledger_txn", columnList = "transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    /** Có dấu: dương = credit (cộng tiền), âm = debit (trừ tiền). VND nguyên. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Số dư ví ngay sau bút toán này — phục vụ sao kê/audit. */
    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "description", length = 255)
    private String description;
}
