package com.venvify.venvifycore.booking.repository;

import com.venvify.venvifycore.booking.entity.TicketTransfer;
import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketTransferRepository extends JpaRepository<TicketTransfer, Long> {

    Optional<TicketTransfer> findByPublicId(String publicId);

    /**
     * Snapshot id-only để accept lock đúng thứ tự Event → Booking rồi mới load transfer.
     * Tránh việc load transfer/booking/event trước lock rồi re-check trên state cũ.
     */
    @Query("select t.id as id, t.booking.id as bookingId, t.booking.event.id as eventId "
            + "from TicketTransfer t where t.publicId = :publicId")
    Optional<TransferLockIds> findLockIdsByPublicId(@Param("publicId") String publicId);

    interface TransferLockIds {
        Long getId();

        Long getBookingId();

        Long getEventId();
    }

    /** Load đủ đồ cho notification listener soạn nội dung — 1 câu, không N+1. */
    @EntityGraph(attributePaths = {"booking", "booking.event", "fromUser", "toUser"})
    Optional<TicketTransfer> findWithDetailsById(Long id);

    /** R-T1: mỗi booking chỉ 1 offer PENDING — gọi dưới lock booking (index (booking_id, status)). */
    boolean existsByBookingIdAndStatus(Long bookingId, TicketTransferStatus status);

    // ---- listMine: 4 biến thể derived thay vì trick ":status is null" (tránh bind null enum) ----

    @EntityGraph(attributePaths = {"booking", "booking.event", "fromUser", "toUser"})
    Page<TicketTransfer> findByFromUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.event", "fromUser", "toUser"})
    Page<TicketTransfer> findByFromUserIdAndStatus(Long userId, TicketTransferStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.event", "fromUser", "toUser"})
    Page<TicketTransfer> findByToUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.event", "fromUser", "toUser"})
    Page<TicketTransfer> findByToUserIdAndStatus(Long userId, TicketTransferStatus status, Pageable pageable);

    /** Offer PENDING quá hạn — expiry job claim id rồi xử lý từng cái 1 tx (pattern EscrowReleaseJob). */
    @Query("select t.id from TicketTransfer t where t.status = :pending and t.expiresAt < :now")
    List<Long> findExpiredPendingIds(@Param("pending") TicketTransferStatus pending, @Param("now") Instant now);

    /** Offer PENDING của mọi booking thuộc event — listener event-cancel hủy hàng loạt. */
    List<TicketTransfer> findByBookingEventIdAndStatus(Long eventId, TicketTransferStatus status);
}
