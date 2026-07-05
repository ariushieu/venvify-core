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
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.service.ResalePaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TicketTransferRepository transferRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserService userService;
    @Mock
    private ResalePaymentService resalePaymentService;
    @Mock
    private TransferMapper transferMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransferService transferService;

    private static final TransferResponse RESPONSE = new TransferResponse(
            "tt-pid", "bkg-pid", "evt-pid", "Title", "sender-pid", "Sender",
            "receiver-pid", "Receiver", 0L, TicketTransferStatus.PENDING, null, null, null, null);

    private User sender;
    private User receiver;
    private Event event;
    private Booking booking;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transferService, "maxHops", 1);
        ReflectionTestUtils.setField(transferService, "expiryHours", 72L);

        sender = user(1L, "sender-pid");
        receiver = user(2L, "receiver-pid");
        event = Event.builder()
                .host(user(9L, "host-pid")).title("Title").slug("title")
                .maxSlots(10).claimedSlots(5).priceAmount(100_000L)
                .status(EventStatus.PUBLISHED)
                .startTime(Instant.now().plusSeconds(3600))
                .build();
        event.setId(100L);
        event.setPublicId("evt-pid");
        booking = Booking.builder()
                .event(event).attendee(sender)
                .status(BookingStatus.CONFIRMED).pricePaid(100_000L)
                .bookedAt(Instant.now()).transferCount(0)
                .build();
        booking.setId(500L);
        booking.setPublicId("bkg-pid");

        BookingRepository.BookingLockIds bookingIds = mock(BookingRepository.BookingLockIds.class);
        lenient().when(bookingIds.getId()).thenReturn(500L);
        lenient().when(bookingIds.getEventId()).thenReturn(100L);

        lenient().when(userService.getByPublicId("sender-pid")).thenReturn(sender);
        lenient().when(userService.getByPublicId("receiver-pid")).thenReturn(receiver);
        lenient().when(bookingRepository.findByPublicId("bkg-pid")).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findLockIdsByPublicId("bkg-pid")).thenReturn(Optional.of(bookingIds));
        lenient().when(bookingRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(booking));
        lenient().when(eventRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(event));
        lenient().when(userService.resolveActiveByEmailOrHandle("receiver@venvify.com", null))
                .thenReturn(receiver);
        lenient().when(transferRepository.save(any(TicketTransfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transferMapper.toResponse(any())).thenReturn(RESPONSE);
        lenient().when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.empty());
    }

    private static User user(long id, String publicId) {
        User u = User.builder()
                .email(publicId + "@venvify.com").fullName("User " + id)
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        u.setId(id);
        u.setPublicId(publicId);
        return u;
    }

    private static CreateTransferRequest request(Long price) {
        return new CreateTransferRequest("receiver@venvify.com", null, price);
    }

    private TicketTransfer pendingTransfer(long price) {
        TicketTransfer transfer = TicketTransfer.builder()
                .booking(booking).fromUser(sender).toUser(receiver)
                .price(price).status(TicketTransferStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        transfer.setId(800L);
        transfer.setPublicId("tt-pid");
        TicketTransferRepository.TransferLockIds ids = mock(TicketTransferRepository.TransferLockIds.class);
        lenient().when(ids.getId()).thenReturn(800L);
        lenient().when(ids.getBookingId()).thenReturn(500L);
        lenient().when(ids.getEventId()).thenReturn(100L);
        lenient().when(transferRepository.findLockIdsByPublicId("tt-pid")).thenReturn(Optional.of(ids));
        lenient().when(transferRepository.findById(800L)).thenReturn(Optional.of(transfer));
        lenient().when(transferRepository.findByPublicId("tt-pid")).thenReturn(Optional.of(transfer));
        return transfer;
    }

    // ---- createOffer ----

    @Test
    void create_gift_savesPendingWithExpiryAndPublishes() {
        transferService.createOffer("sender-pid", "bkg-pid", request(0L));

        ArgumentCaptor<TicketTransfer> saved = ArgumentCaptor.forClass(TicketTransfer.class);
        verify(transferRepository).save(saved.capture());
        TicketTransfer transfer = saved.getValue();
        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.PENDING);
        assertThat(transfer.getPrice()).isZero();
        assertThat(transfer.getFromUser()).isSameAs(sender);
        assertThat(transfer.getToUser()).isSameAs(receiver);
        assertThat(transfer.getExpiresAt()).isAfter(Instant.now().plusSeconds(71 * 3600));

        ArgumentCaptor<TicketTransferChangedEvent> published =
                ArgumentCaptor.forClass(TicketTransferChangedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().status()).isEqualTo(TicketTransferStatus.PENDING);
    }

    @Test
    void create_notTicketOwner_forbidden() {
        User stranger = user(3L, "stranger-pid");
        when(userService.getByPublicId("stranger-pid")).thenReturn(stranger);

        assertThatThrownBy(() -> transferService.createOffer("stranger-pid", "bkg-pid", request(0L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_ownerChangedUnderLock_forbidden() {
        booking.setAttendee(receiver);

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(ForbiddenException.class);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void create_toSelf_rejected() {
        when(userService.resolveActiveByEmailOrHandle("sender@venvify.com", null)).thenReturn(sender);

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid",
                new CreateTransferRequest("sender@venvify.com", null, 0L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void create_priceAboveOriginal_rejectedR1() {
        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(100_001L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("original ticket price");
    }

    @Test
    void create_hopLimitReached_rejectedR2() {
        booking.setTransferCount(1);

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transfer limit");
    }

    @Test
    void create_eventAlreadyStarted_rejectedR3() {
        event.setStartTime(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("after the event starts");
    }

    @Test
    void create_eventNotPublished_rejected() {
        event.setStatus(EventStatus.CANCELLED);

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_bookingNotConfirmed_rejected() {
        booking.setStatus(BookingStatus.REFUNDED);

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_secondPendingOffer_conflictRT1() {
        when(transferRepository.existsByBookingIdAndStatus(500L, TicketTransferStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_receiverAlreadyHasBooking_conflict() {
        // Kể cả row CANCELLED — UNIQUE(event, attendee) không cho sang tên trúng row trùng (MVP).
        Booking existing = Booking.builder()
                .event(event).attendee(receiver)
                .status(BookingStatus.CANCELLED).pricePaid(0L).bookedAt(Instant.now())
                .build();
        when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transferService.createOffer("sender-pid", "bkg-pid", request(0L)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- accept ----

    @Test
    void accept_gift_transfersOwnershipWithoutPayment() {
        pendingTransfer(0L);

        transferService.accept("receiver-pid", "tt-pid");

        assertThat(booking.getAttendee()).isSameAs(receiver);
        assertThat(booking.getTransferCount()).isEqualTo(1);
        verify(bookingRepository).save(booking);
        verify(resalePaymentService, never()).payTicketResale(any(), any(), anyLong(), any());

        ArgumentCaptor<TicketTransfer> saved = ArgumentCaptor.forClass(TicketTransfer.class);
        verify(transferRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TicketTransferStatus.COMPLETED);
        assertThat(saved.getValue().getCompletedAt()).isNotNull();
        // Khóa đúng thứ tự toàn cục: event trước, booking sau (R13).
        verify(eventRepository).findByIdForUpdate(100L);
        verify(bookingRepository).findByIdForUpdate(500L);
    }

    @Test
    void accept_paidResale_paysViaWalletAndLinksTransaction() {
        TicketTransfer transfer = pendingTransfer(80_000L);
        Transaction txn = Transaction.builder().transactionRef("RSL-x").build();
        when(resalePaymentService.payTicketResale(receiver, sender, 80_000L, event)).thenReturn(txn);

        transferService.accept("receiver-pid", "tt-pid");

        verify(resalePaymentService).payTicketResale(receiver, sender, 80_000L, event);
        assertThat(transfer.getTransaction()).isSameAs(txn);
        assertThat(booking.getAttendee()).isSameAs(receiver);
    }

    @Test
    void accept_insufficientBalance_propagatesBeforeOwnershipChange() {
        pendingTransfer(80_000L);
        when(resalePaymentService.payTicketResale(receiver, sender, 80_000L, event))
                .thenThrow(new BadRequestException("Insufficient wallet balance"));

        assertThatThrownBy(() -> transferService.accept("receiver-pid", "tt-pid"))
                .isInstanceOf(BadRequestException.class);

        assertThat(booking.getAttendee()).isSameAs(sender);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void accept_notTheReceiver_forbidden() {
        pendingTransfer(0L);

        assertThatThrownBy(() -> transferService.accept("sender-pid", "tt-pid"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void accept_expiredOffer_rejectedWithoutMutation() {
        TicketTransfer transfer = pendingTransfer(0L);
        transfer.setExpiresAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> transferService.accept("receiver-pid", "tt-pid"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");

        // Không tự đánh EXPIRED trong tx bị rollback — job expiry lo.
        verify(transferRepository, never()).save(any());
    }

    @Test
    void accept_eventCancelledUnderLock_rejected() {
        // Event vừa bị hủy giữa lúc xem offer và lúc accept — re-check dưới khóa chặn lại,
        // tiền không chảy, vé không sang tên (refund của event hủy đã về đúng chủ cũ).
        pendingTransfer(0L);
        event.setStatus(EventStatus.CANCELLED);

        assertThatThrownBy(() -> transferService.accept("receiver-pid", "tt-pid"))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void accept_receiverGotTicketMeanwhile_conflict() {
        pendingTransfer(0L);
        Booking other = Booking.builder()
                .event(event).attendee(receiver)
                .status(BookingStatus.CONFIRMED).pricePaid(0L).bookedAt(Instant.now())
                .build();
        when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> transferService.accept("receiver-pid", "tt-pid"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void accept_notPending_conflict() {
        TicketTransfer transfer = pendingTransfer(0L);
        transfer.setStatus(TicketTransferStatus.DECLINED);

        assertThatThrownBy(() -> transferService.accept("receiver-pid", "tt-pid"))
                .isInstanceOf(ConflictException.class);
    }

    // ---- decline / cancel ----

    @Test
    void decline_byReceiver_setsDeclined() {
        TicketTransfer transfer = pendingTransfer(0L);

        transferService.decline("receiver-pid", "tt-pid");

        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.DECLINED);
    }

    @Test
    void decline_bySender_forbidden() {
        pendingTransfer(0L);

        assertThatThrownBy(() -> transferService.decline("sender-pid", "tt-pid"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_bySender_setsCancelled() {
        TicketTransfer transfer = pendingTransfer(0L);

        transferService.cancel("sender-pid", "tt-pid");

        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCompleted_conflict() {
        TicketTransfer transfer = pendingTransfer(0L);
        transfer.setStatus(TicketTransferStatus.COMPLETED);

        assertThatThrownBy(() -> transferService.cancel("sender-pid", "tt-pid"))
                .isInstanceOf(ConflictException.class);
    }

    // ---- expireOne / cancelPendingForEvent ----

    @Test
    void expireOne_pendingPastDue_marksExpired() {
        TicketTransfer transfer = pendingTransfer(0L);
        transfer.setExpiresAt(Instant.now().minusSeconds(60));
        when(transferRepository.findById(800L)).thenReturn(Optional.of(transfer));

        transferService.expireOne(800L);

        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.EXPIRED);
        verify(eventPublisher).publishEvent(any(TicketTransferChangedEvent.class));
    }

    @Test
    void expireOne_alreadyCompleted_noop() {
        TicketTransfer transfer = pendingTransfer(0L);
        transfer.setStatus(TicketTransferStatus.COMPLETED);
        when(transferRepository.findById(800L)).thenReturn(Optional.of(transfer));

        transferService.expireOne(800L);

        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.COMPLETED);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void expireOne_notYetDue_noop() {
        TicketTransfer transfer = pendingTransfer(0L);
        when(transferRepository.findById(800L)).thenReturn(Optional.of(transfer));

        transferService.expireOne(800L);

        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.PENDING);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void cancelPendingForEvent_cancelsEveryPendingOffer() {
        TicketTransfer t1 = pendingTransfer(0L);
        TicketTransfer t2 = TicketTransfer.builder()
                .booking(booking).fromUser(sender).toUser(receiver)
                .price(0L).status(TicketTransferStatus.PENDING)
                .build();
        t2.setId(801L);
        when(transferRepository.findByBookingEventIdAndStatus(100L, TicketTransferStatus.PENDING))
                .thenReturn(List.of(t1, t2));

        transferService.cancelPendingForEvent(100L);

        assertThat(t1.getStatus()).isEqualTo(TicketTransferStatus.CANCELLED);
        assertThat(t2.getStatus()).isEqualTo(TicketTransferStatus.CANCELLED);
    }

    // ---- listMine ----

    @Test
    void listMine_receivedWithoutStatus_usesToUserQuery() {
        when(transferRepository.findByToUserId(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transferService.listMine("receiver-pid", TransferRole.RECEIVED, null, 0, 20);

        verify(transferRepository).findByToUserId(eq(2L), any(Pageable.class));
    }

    @Test
    void listMine_sentWithStatus_usesFromUserStatusQuery() {
        when(transferRepository.findByFromUserIdAndStatus(eq(1L), eq(TicketTransferStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transferService.listMine("sender-pid", TransferRole.SENT, TicketTransferStatus.PENDING, 0, 20);

        verify(transferRepository).findByFromUserIdAndStatus(
                eq(1L), eq(TicketTransferStatus.PENDING), any(Pageable.class));
    }
}
