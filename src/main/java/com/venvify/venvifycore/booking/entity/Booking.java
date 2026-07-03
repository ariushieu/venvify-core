package com.venvify.venvifycore.booking.entity;

import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.event.entity.Event;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_booking_event_attendee",
                columnNames = {"event_id", "attendee_id"}
        ),
        indexes = {
                @Index(name = "idx_bookings_attendee", columnList = "attendee_id"),
                @Index(name = "idx_bookings_event", columnList = "event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendee_id", nullable = false)
    private User attendee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    /** Giá đã trả (snapshot lúc đặt, VND nguyên). */
    @Column(name = "price_paid", nullable = false)
    @Builder.Default
    private Long pricePaid = 0L;

    /** Giao dịch mua vé. NULL nếu event free. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_txn_id")
    private Transaction purchaseTransaction;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    /** Số lần vé đã được pass (R2 — giới hạn app.booking.transfer-max-hops, chống rửa vé lòng vòng). */
    @Column(name = "transfer_count", nullable = false)
    @Builder.Default
    private Integer transferCount = 0;
}
