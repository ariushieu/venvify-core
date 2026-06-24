package com.venvify.venvifycore.wallet.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.wallet.enums.PaymentProvider;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_txn_ref", columnNames = "transaction_ref"),
        indexes = {
                @Index(name = "idx_txn_type", columnList = "type"),
                @Index(name = "idx_txn_status", columnList = "status"),
                @Index(name = "idx_txn_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /** VND nguyên (D6). */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Khóa idempotency: callback VNPay/MoMo lặp cùng ref → bỏ qua, không tạo bút toán mới. */
    @Column(name = "transaction_ref", nullable = false, unique = true, length = 100)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 20)
    private PaymentProvider paymentProvider;

    @Column(name = "provider_txn_id", length = 100)
    private String providerTxnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;
}
