package com.venvify.venvifycore.booking.service;

import com.venvify.venvifycore.booking.dto.BookingResponse;
import com.venvify.venvifycore.booking.dto.CreateBookingRequest;
import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.booking.mapper.BookingMapper;
import com.venvify.venvifycore.booking.repository.BookingRepository;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.repository.UserRepository;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Nghiệp vụ đặt vé (plan §3; money-core §3.1–3.2). Event FREE → CONFIRMED ngay; event có phí
 * mua bằng ví — trừ tiền NGAY, giữ vào escrow (đường Sepay QR/RESERVED là slice sau).
 * Chống oversell bằng khóa row event (D4).
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final EscrowService escrowService;

    /**
     * Đặt vé: kiểm tra mở bán, trùng vé, hết chỗ; tăng claimed_slots dưới khóa row (plan §3.1).
     * Event có phí: trừ ví NGAY qua escrow (money-core §3.1) — thiếu tiền → exception → rollback
     * cả slot. Thứ tự khóa R13: event trước → ví sau (trong LedgerService, id tăng dần).
     */
    @Transactional
    public BookingResponse create(String userPublicId, CreateBookingRequest request) {
        User attendee = requireUser(userPublicId);

        Event event = eventRepository.findByPublicId(request.eventPublicId())
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BadRequestException("Event is not open for booking");
        }
        if (event.getHost().getId().equals(attendee.getId())) {
            throw new BadRequestException("You cannot book your own event");
        }

        Booking existing = bookingRepository
                .findByEventIdAndAttendeeId(event.getId(), attendee.getId())
                .orElse(null);
        if (existing != null && existing.getStatus() != BookingStatus.CANCELLED) {
            // Gồm cả REFUNDED: event đã hủy thì không mua lại được (Đ-P2.6).
            throw new ConflictException("You have already booked this event");
        }

        // Khóa row event để cập nhật claimed_slots an toàn khi nhiều người đặt cùng lúc (D4).
        Event locked = eventRepository.findByIdForUpdate(event.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (locked.getStatus() != EventStatus.PUBLISHED) {
            // Re-check dưới khóa: event có thể vừa bị hủy giữa lúc check ở trên và lúc khóa —
            // nếu lọt qua, tiền sẽ vào escrow của event chết mà không ai refund.
            throw new BadRequestException("Event is not open for booking");
        }
        if (locked.getClaimedSlots() >= locked.getMaxSlots()) {
            throw new BadRequestException("Event is sold out");
        }
        locked.setClaimedSlots(locked.getClaimedSlots() + 1);
        eventRepository.save(locked);

        long price = locked.getPriceAmount() == null ? 0L : locked.getPriceAmount();

        Booking booking = existing != null ? existing : Booking.builder()
                .event(locked)
                .attendee(attendee)
                .build();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPricePaid(price);
        booking.setBookedAt(Instant.now());
        booking = bookingRepository.save(booking);

        if (price > 0) {
            // Booking đã persist (hold cần FK); thiếu tiền → BadRequest → rollback cả slot lẫn booking.
            Transaction purchase = escrowService.holdTicketPayment(booking, attendee, price);
            booking.setPurchaseTransaction(purchase);
            booking = bookingRepository.save(booking);
        }

        return bookingMapper.toResponse(booking);
    }

    /** Vé của user đang đăng nhập. */
    @Transactional(readOnly = true)
    public PagedResponse<BookingResponse> listMine(String userPublicId, Pageable pageable) {
        User attendee = requireUser(userPublicId);
        Page<Booking> page = bookingRepository.findByAttendeeId(attendee.getId(), pageable);
        return PagedResponse.of(page.map(bookingMapper::toResponse));
    }

    /** Chi tiết vé: chỉ attendee sở hữu hoặc host của event được xem (plan §3). */
    @Transactional(readOnly = true)
    public BookingResponse getDetail(String userPublicId, String bookingPublicId) {
        Booking booking = requireBooking(bookingPublicId);

        boolean isAttendee = booking.getAttendee().getPublicId().equals(userPublicId);
        boolean isHost = booking.getEvent().getHost().getPublicId().equals(userPublicId);
        if (!isAttendee && !isHost) {
            throw new ForbiddenException("You do not have access to this booking");
        }

        return bookingMapper.toResponse(booking);
    }

    /**
     * Huỷ vé FREE trước giờ bắt đầu; trả lại slot dưới khóa row (plan §3). Chỉ attendee tự huỷ.
     * Vé PAID không tự huỷ được (O-M2): refund chỉ khi event bị hủy; không đi được → pass vé
     * (transfer — slice sau). Bảo vệ host khỏi huỷ hàng loạt sát giờ + đơn giản hóa kế toán.
     */
    @Transactional
    public BookingResponse cancel(String userPublicId, String bookingPublicId) {
        Booking booking = requireBooking(bookingPublicId);

        if (!booking.getAttendee().getPublicId().equals(userPublicId)) {
            throw new ForbiddenException("You can only cancel your own booking");
        }
        if (booking.getStatus() != BookingStatus.RESERVED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Booking cannot be cancelled");
        }
        if (booking.getPricePaid() != null && booking.getPricePaid() > 0) {
            throw new BadRequestException("Paid bookings cannot be cancelled — transfer your ticket instead");
        }

        Instant startTime = booking.getEvent().getStartTime();
        if (startTime != null && !Instant.now().isBefore(startTime)) {
            throw new BadRequestException("Cannot cancel after the event has started");
        }

        Event locked = eventRepository.findByIdForUpdate(booking.getEvent().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (locked.getClaimedSlots() > 0) {
            locked.setClaimedSlots(locked.getClaimedSlots() - 1);
            eventRepository.save(locked);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    /**
     * Attendee cần báo khi event bị hủy: vé paid đã thành REFUNDED (escrow hoàn xong),
     * vé free vẫn CONFIRMED. Đọc cho NotificationListener (master §2 amend 2026-07-04).
     */
    @Transactional(readOnly = true)
    public List<Booking> listForEventCancelNotice(Long eventId) {
        return bookingRepository.findByEventIdAndStatusIn(eventId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.REFUNDED));
    }

    // ----- helpers -----

    private User requireUser(String userPublicId) {
        return userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Booking requireBooking(String bookingPublicId) {
        return bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }
}
