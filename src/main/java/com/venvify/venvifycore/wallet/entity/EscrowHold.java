package com.venvify.venvifycore.wallet.entity;

import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
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
 * Giữ tiền escrow theo từng booking (D3). State machine: HELD → RELEASED → PAID_OUT, nhánh REFUNDED.
 */
@Entity
@Table(name = "escrow_holds", indexes = {
        @Index(name = "idx_escrow_event", columnList = "event_id"),
        @Index(name = "idx_escrow_booking", columnList = "booking_id"),
        @Index(name = "idx_escrow_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscrowHold extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Tiền vé gốc (VND). */
    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    /** Phí platform giữ lại. */
    @Column(name = "commission_amount", nullable = false)
    private Long commissionAmount;

    /** Phần host nhận = gross - commission. */
    @Column(name = "host_net_amount", nullable = false)
    private Long hostNetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EscrowStatus status;

    @Column(name = "held_at", nullable = false)
    private Instant heldAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    /** Mốc audit các nhánh còn lại của state machine (F4). */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;
}
