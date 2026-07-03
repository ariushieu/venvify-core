package com.venvify.venvifycore.booking.entity;

import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.wallet.entity.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;

/**
 * Offer chuyển nhượng vé — handshake trực tiếp tới 1 người (D9), kiêm bản ghi lịch sử
 * (append sau COMPLETED, không sửa). Giá pass ∈ [0, price_paid] (D10 — R1); tặng = price 0.
 * R-T1: mỗi booking chỉ 1 PENDING tại một thời điểm — enforce ở service dưới lock booking
 * (MySQL không có partial unique index), index (booking_id, status) phục vụ check đó.
 */
@Entity
@Table(name = "ticket_transfers", indexes = {
        @Index(name = "idx_tt_booking_status", columnList = "booking_id, status"),
        @Index(name = "idx_tt_to_status", columnList = "to_user_id, status"),
        @Index(name = "idx_tt_from", columnList = "from_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTransfer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Chủ vé lúc tạo offer. Vé vẫn của người này tới khi COMPLETED. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    /** Người nhận (resolve từ email/handle — R4: phải là user đã đăng ký). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    /** Giá pass (VND nguyên). 0 = tặng. RULE R1: 0 ≤ price ≤ booking.price_paid. */
    @Column(name = "price", nullable = false)
    @Builder.Default
    private Long price = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TicketTransferStatus status;

    /** Txn TICKET_RESALE khi bán lại có giá. NULL nếu tặng (price = 0). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    /** TTL của offer (72h — app.booking.transfer-expiry-hours); quá hạn job đánh EXPIRED. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
