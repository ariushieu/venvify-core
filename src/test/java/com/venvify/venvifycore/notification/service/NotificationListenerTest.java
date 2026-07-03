package com.venvify.venvifycore.notification.service;

import com.venvify.venvifycore.booking.domain.TicketTransferChangedEvent;
import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.entity.TicketTransfer;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.booking.service.TransferService;
import com.venvify.venvifycore.common.email.EmailService;
import com.venvify.venvifycore.event.domain.EventCancelledEvent;
import com.venvify.venvifycore.event.domain.EventPublishedEvent;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.notification.enums.NotificationType;
import com.venvify.venvifycore.social.service.FollowService;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private TransferService transferService;
    @Mock
    private BookingService bookingService;
    @Mock
    private EventService eventService;
    @Mock
    private FollowService followService;

    @InjectMocks
    private NotificationListener listener;

    private User sender;
    private User receiver;
    private Event event;
    private TicketTransfer transfer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "notifyEmailMaxFollowers", 500);

        sender = user(1L, "sender-pid");
        receiver = user(2L, "receiver-pid");
        User host = user(9L, "host-pid");
        event = Event.builder()
                .host(host).title("Title").slug("title")
                .maxSlots(10).claimedSlots(1).priceAmount(100_000L)
                .status(EventStatus.PUBLISHED)
                .build();
        event.setId(100L);
        event.setPublicId("evt-pid");

        Booking booking = Booking.builder()
                .event(event).attendee(sender)
                .status(BookingStatus.CONFIRMED).pricePaid(100_000L).bookedAt(Instant.now())
                .build();
        booking.setId(500L);

        transfer = TicketTransfer.builder()
                .booking(booking).fromUser(sender).toUser(receiver)
                .price(0L).status(TicketTransferStatus.PENDING)
                .build();
        transfer.setId(800L);
        transfer.setPublicId("tt-pid");
        lenient().when(transferService.loadWithDetails(800L)).thenReturn(transfer);
        lenient().when(eventService.loadWithHost(100L)).thenReturn(event);
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

    // ---- transfer ----

    @Test
    void transferPending_notifiesAndEmailsReceiver() {
        listener.onTransferChanged(new TicketTransferChangedEvent(800L, TicketTransferStatus.PENDING));

        verify(notificationService).dispatch(eq(NotificationType.TRANSFER_OFFER_RECEIVED), eq(receiver),
                anyString(), anyString(), eq("TICKET_TRANSFER"), eq("tt-pid"));
        verify(emailService).sendNotificationEmail(eq(receiver.getEmail()), anyString(), anyString());
    }

    @Test
    void transferCompleted_notifiesBothSidesEmailsSender() {
        listener.onTransferChanged(new TicketTransferChangedEvent(800L, TicketTransferStatus.COMPLETED));

        verify(notificationService).dispatch(eq(NotificationType.TRANSFER_COMPLETED), eq(sender),
                anyString(), anyString(), anyString(), anyString());
        verify(notificationService).dispatch(eq(NotificationType.TRANSFER_COMPLETED), eq(receiver),
                anyString(), anyString(), anyString(), anyString());
        verify(emailService).sendNotificationEmail(eq(sender.getEmail()), anyString(), anyString());
    }

    @Test
    void transferDeclined_notifiesSenderOnly() {
        listener.onTransferChanged(new TicketTransferChangedEvent(800L, TicketTransferStatus.DECLINED));

        verify(notificationService).dispatch(eq(NotificationType.TRANSFER_DECLINED), eq(sender),
                anyString(), anyString(), anyString(), anyString());
        verify(emailService, never()).sendNotificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void transferCancelled_notifiesReceiver() {
        listener.onTransferChanged(new TicketTransferChangedEvent(800L, TicketTransferStatus.CANCELLED));

        verify(notificationService).dispatch(eq(NotificationType.TRANSFER_CANCELLED), eq(receiver),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void transferListener_swallowsFailures() {
        when(transferService.loadWithDetails(800L)).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> listener.onTransferChanged(
                new TicketTransferChangedEvent(800L, TicketTransferStatus.PENDING)))
                .doesNotThrowAnyException();
    }

    // ---- event cancelled ----

    @Test
    void eventCancelled_splitsRefundedAndFreeMessagesAndEmailsAll() {
        Booking paid = Booking.builder().event(event).attendee(sender)
                .status(BookingStatus.REFUNDED).pricePaid(100_000L).bookedAt(Instant.now()).build();
        Booking free = Booking.builder().event(event).attendee(receiver)
                .status(BookingStatus.CONFIRMED).pricePaid(0L).bookedAt(Instant.now()).build();
        when(bookingService.listForEventCancelNotice(100L)).thenReturn(List.of(paid, free));

        listener.onEventCancelled(new EventCancelledEvent(100L));

        verify(notificationService).dispatchBatch(eq(NotificationType.EVENT_CANCELLED), eq(List.of(1L)),
                anyString(), contains("refunded"), eq("EVENT"), eq("evt-pid"));
        verify(notificationService).dispatchBatch(eq(NotificationType.EVENT_CANCELLED), eq(List.of(2L)),
                anyString(), anyString(), eq("EVENT"), eq("evt-pid"));
        verify(emailService, times(2)).sendNotificationEmail(anyString(), anyString(), anyString());
    }

    // ---- event published fan-out ----

    @Test
    void eventPublished_noFollowers_noWork() {
        when(followService.listFollowerIds(9L)).thenReturn(List.of());

        listener.onEventPublished(new EventPublishedEvent(100L));

        verify(notificationService, never()).dispatchBatch(any(), anyList(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void eventPublished_underCap_batchNotificationAndEmails() {
        when(followService.listFollowerIds(9L)).thenReturn(List.of(11L, 12L));
        when(followService.listFollowers(9L)).thenReturn(List.of(user(11L, "f1"), user(12L, "f2")));

        listener.onEventPublished(new EventPublishedEvent(100L));

        verify(notificationService).dispatchBatch(eq(NotificationType.NEW_EVENT_FROM_FOLLOWED_HOST),
                eq(List.of(11L, 12L)), anyString(), anyString(), eq("EVENT"), eq("evt-pid"));
        verify(emailService, times(2)).sendNotificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void eventPublished_overCap_skipsEmails() {
        ReflectionTestUtils.setField(listener, "notifyEmailMaxFollowers", 1);
        when(followService.listFollowerIds(9L)).thenReturn(List.of(11L, 12L));

        listener.onEventPublished(new EventPublishedEvent(100L));

        // In-app vẫn đủ 2 người; email bị cap (trade-off P6 §1).
        verify(notificationService).dispatchBatch(eq(NotificationType.NEW_EVENT_FROM_FOLLOWED_HOST),
                eq(List.of(11L, 12L)), anyString(), anyString(), anyString(), anyString());
        verify(followService, never()).listFollowers(any());
        verify(emailService, never()).sendNotificationEmail(anyString(), anyString(), anyString());
    }
}
