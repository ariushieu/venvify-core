package com.venvify.venvifycore.booking.service;

import com.venvify.venvifycore.booking.domain.TicketTransferChangedEvent;
import com.venvify.venvifycore.booking.dto.CreateTransferRequest;
import com.venvify.venvifycore.booking.dto.TransferResponse;
import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.entity.TicketTransfer;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import com.venvify.venvifycore.booking.enums.TransferRole;
import com.venvify.venvifycore.booking.mapper.TransferMapper;
import com.venvify.venvifycore.booking.repository.BookingRepository;
import com.venvify.venvifycore.booking.repository.TicketTransferRepository;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.service.ResalePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Chuyển nhượng vé — handshake PENDING → accept/decline/cancel/expire (plan P3 §1,
 * nền 20260630 D8–D13, R1–R4). Tiền resale đi ResalePaymentService (đường ví; QR chờ P2).
 *
 * <p>Chống race: accept khóa theo thứ tự toàn cục Event → Booking → ví (R13); mọi mutation
 * transfer đều có {@code @Version} làm chốt cuối (2 accept/accept-vs-expire song song →
 * kẻ đến sau ăn OptimisticLockingFailure → 409). Trả tiền và sang tên CÙNG transaction —
 * PENDING không bao giờ giữ tiền của ai (plan §1.4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TicketTransferRepository transferRepository;
    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final UserService userService;
    private final ResalePaymentService resalePaymentService;
    private final TransferMapper transferMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** O2 — chống rửa vé lòng vòng. */
    @Value("${app.booking.transfer-max-hops:1}")
    private int maxHops;

    /** TTL offer (giờ). */
    @Value("${app.booking.transfer-expiry-hours:72}")
    private long expiryHours;

    /**
     * Sender tạo offer (plan §1.5). R-T1 (1 PENDING/booking) check dưới khóa booking —
     * MySQL không có partial unique index nên khóa row là chốt duy nhất.
     */
    @Transactional
    public TransferResponse createOffer(String senderPublicId, String bookingPublicId,
                                        CreateTransferRequest request) {
        User sender = userService.getByPublicId(senderPublicId);
        Booking booking = requireBooking(bookingPublicId);

        if (!booking.getAttendee().getId().equals(sender.getId())) {
            throw new ForbiddenException("You do not own this ticket");
        }

        User receiver = userService.resolveActiveByEmailOrHandle(request.toUserEmail(), request.toUserHandle());
        if (receiver.getId().equals(sender.getId())) {
            throw new BadRequestException("You cannot transfer a ticket to yourself");
        }

        long price = request.price() == null ? 0L : request.price();
        if (price > booking.getPricePaid()) {
            // R1 — chống phe vé: giá pass ≤ giá gốc (D10).
            throw new BadRequestException("Transfer price cannot exceed the original ticket price");
        }

        // Khóa booking rồi re-check mọi điều kiện trạng thái dưới khóa (R-T1 + R2 + R3).
        Booking locked = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        verifyTransferable(locked);
        if (transferRepository.existsByBookingIdAndStatus(locked.getId(), TicketTransferStatus.PENDING)) {
            throw new ConflictException("A pending transfer already exists for this ticket");
        }
        verifyReceiverHasNoBooking(locked.getEvent(), receiver);

        TicketTransfer transfer = transferRepository.save(TicketTransfer.builder()
                .booking(locked)
                .fromUser(sender)
                .toUser(receiver)
                .price(price)
                .status(TicketTransferStatus.PENDING)
                .expiresAt(Instant.now().plus(Duration.ofHours(expiryHours)))
                .build());

        publishChanged(transfer);
        return transferMapper.toResponse(transfer);
    }

    /**
     * Receiver chấp nhận (plan §1.2–1.3): 1 transaction — khóa Event → Booking (R13),
     * re-verify toàn bộ, trả tiền nếu có giá (ví; thiếu tiền rollback tất), sang tên,
     * transfer_count++, COMPLETED. Escrow vé gốc KHÔNG đụng (D10).
     */
    @Transactional
    public TransferResponse accept(String receiverPublicId, String transferPublicId) {
        TicketTransfer transfer = requireTransfer(transferPublicId);
        if (!transfer.getToUser().getPublicId().equals(receiverPublicId)) {
            throw new ForbiddenException("This transfer was not offered to you");
        }
        requirePending(transfer);
        if (transfer.getExpiresAt() != null && !Instant.now().isBefore(transfer.getExpiresAt())) {
            // Không set EXPIRED ở đây (throw sẽ rollback luôn cả mutation) — job expiry lo việc đánh dấu.
            throw new BadRequestException("Transfer offer has expired");
        }

        // Khóa event trước (thứ tự toàn cục R13) — serialize với EventService.cancel:
        // không có chuyện sang tên xong mà refund lại chảy về chủ cũ.
        Event event = eventRepository.findByIdForUpdate(transfer.getBooking().getEvent().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        Booking booking = bookingRepository.findByIdForUpdate(transfer.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        verifyTransferable(booking);
        if (!booking.getAttendee().getId().equals(transfer.getFromUser().getId())) {
            // Vé đã đổi chủ từ lúc tạo offer (không xảy ra khi R-T1 giữ đúng — defensive).
            throw new ConflictException("Ticket owner has changed since this offer was created");
        }
        User receiver = transfer.getToUser();
        verifyReceiverHasNoBooking(event, receiver);

        if (transfer.getPrice() > 0) {
            Transaction txn = resalePaymentService.payTicketResale(
                    receiver, transfer.getFromUser(), transfer.getPrice(), event);
            transfer.setTransaction(txn);
        }

        booking.setAttendee(receiver);
        booking.setTransferCount(booking.getTransferCount() + 1);
        bookingRepository.save(booking);

        transfer.setStatus(TicketTransferStatus.COMPLETED);
        transfer.setCompletedAt(Instant.now());
        transferRepository.save(transfer);

        publishChanged(transfer);
        return transferMapper.toResponse(transfer);
    }

    /** Receiver từ chối offer PENDING. */
    @Transactional
    public TransferResponse decline(String receiverPublicId, String transferPublicId) {
        TicketTransfer transfer = requireTransfer(transferPublicId);
        if (!transfer.getToUser().getPublicId().equals(receiverPublicId)) {
            throw new ForbiddenException("This transfer was not offered to you");
        }
        requirePending(transfer);

        transfer.setStatus(TicketTransferStatus.DECLINED);
        transferRepository.save(transfer);

        publishChanged(transfer);
        return transferMapper.toResponse(transfer);
    }

    /** Sender rút lại offer PENDING (DELETE — record giữ nguyên làm lịch sử, chỉ đổi status). */
    @Transactional
    public TransferResponse cancel(String senderPublicId, String transferPublicId) {
        TicketTransfer transfer = requireTransfer(transferPublicId);
        if (!transfer.getFromUser().getPublicId().equals(senderPublicId)) {
            throw new ForbiddenException("You did not create this transfer");
        }
        requirePending(transfer);

        transfer.setStatus(TicketTransferStatus.CANCELLED);
        transferRepository.save(transfer);

        publishChanged(transfer);
        return transferMapper.toResponse(transfer);
    }

    /** Offer của tôi theo vai gửi/nhận, lọc status tuỳ chọn (plan §1.5). */
    @Transactional(readOnly = true)
    public PagedResponse<TransferResponse> listMine(String userPublicId, TransferRole role,
                                                    TicketTransferStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        User user = userService.getByPublicId(userPublicId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<TicketTransfer> result;
        if (role == TransferRole.RECEIVED) {
            result = status == null
                    ? transferRepository.findByToUserId(user.getId(), pageable)
                    : transferRepository.findByToUserIdAndStatus(user.getId(), status, pageable);
        } else {
            result = status == null
                    ? transferRepository.findByFromUserId(user.getId(), pageable)
                    : transferRepository.findByFromUserIdAndStatus(user.getId(), status, pageable);
        }
        return PagedResponse.of(result.map(transferMapper::toResponse));
    }

    /**
     * Job expiry gọi theo id, mỗi offer 1 tx riêng (pattern EscrowReleaseJob). Re-check
     * trong tx → idempotent; đua với accept thì {@code @Version} phân thắng thua.
     */
    @Transactional
    public void expireOne(Long transferId) {
        TicketTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateException("Transfer not found: " + transferId));
        if (transfer.getStatus() != TicketTransferStatus.PENDING
                || transfer.getExpiresAt() == null
                || Instant.now().isBefore(transfer.getExpiresAt())) {
            return; // đã xử lý ở tx khác hoặc chưa tới hạn
        }
        transfer.setStatus(TicketTransferStatus.EXPIRED);
        transferRepository.save(transfer);
        publishChanged(transfer);
    }

    /**
     * Event bị hủy → hủy mọi offer PENDING của event đó (plan §1.4). Chạy từ listener
     * AFTER_COMMIT của {@code EventCancelledEvent} — {@code REQUIRES_NEW} vì tx gốc đã
     * commit xong, listener cần tx mới thật sự (không phải lồng trong luồng tiền — R18 ok).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelPendingForEvent(Long eventId) {
        List<TicketTransfer> pending =
                transferRepository.findByBookingEventIdAndStatus(eventId, TicketTransferStatus.PENDING);
        for (TicketTransfer transfer : pending) {
            transfer.setStatus(TicketTransferStatus.CANCELLED);
            transferRepository.save(transfer);
            publishChanged(transfer);
        }
        if (!pending.isEmpty()) {
            log.info("Cancelled {} pending transfer(s) of cancelled event {}", pending.size(), eventId);
        }
    }

    // ----- helpers -----

    /** R2 + R3/O3 + trạng thái event — dùng chung cho create (dưới khóa booking) và accept. */
    private void verifyTransferable(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed tickets can be transferred");
        }
        if (booking.getTransferCount() >= maxHops) {
            throw new BadRequestException("This ticket has reached its transfer limit");
        }
        Event event = booking.getEvent();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            // Gồm CANCELLED/ENDED/POSTPONED — lịch không chắc chắn thì không cho sang tên.
            throw new BadRequestException("Event is not open for ticket transfer");
        }
        if (event.getStartTime() == null || !Instant.now().isBefore(event.getStartTime())) {
            throw new BadRequestException("Tickets can no longer be transferred after the event starts");
        }
    }

    /**
     * Receiver đã có booking-row event này (kể cả CANCELLED) → 409. Row CANCELLED chặn luôn
     * là limitation MVP có chủ đích: UNIQUE(event_id, attendee_id) không cho sang tên trúng
     * row trùng, mà xóa cứng row cũ thì dính FK tiền (amend log plan P3).
     */
    private void verifyReceiverHasNoBooking(Event event, User receiver) {
        bookingRepository.findByEventIdAndAttendeeId(event.getId(), receiver.getId())
                .ifPresent(existing -> {
                    throw new ConflictException("Receiver already has a booking for this event");
                });
    }

    private Booking requireBooking(String publicId) {
        return bookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private TicketTransfer requireTransfer(String publicId) {
        return transferRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
    }

    private static void requirePending(TicketTransfer transfer) {
        if (transfer.getStatus() != TicketTransferStatus.PENDING) {
            throw new ConflictException("Transfer is no longer pending");
        }
    }

    /** Notification listener (P6) nghe AFTER_COMMIT — publish trong tx, nổ sau commit thật. */
    private void publishChanged(TicketTransfer transfer) {
        eventPublisher.publishEvent(new TicketTransferChangedEvent(transfer.getId(), transfer.getStatus()));
    }
}
