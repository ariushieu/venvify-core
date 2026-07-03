package com.venvify.venvifycore.notification.service;

import com.venvify.venvifycore.booking.domain.TicketTransferChangedEvent;
import com.venvify.venvifycore.booking.entity.Booking;
import com.venvify.venvifycore.booking.entity.TicketTransfer;
import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.booking.service.TransferService;
import com.venvify.venvifycore.common.email.EmailService;
import com.venvify.venvifycore.event.domain.EventCancelledEvent;
import com.venvify.venvifycore.event.domain.EventPublishedEvent;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.notification.enums.NotificationType;
import com.venvify.venvifycore.social.service.FollowService;
import com.venvify.venvifycore.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Nguồn phát notification DUY NHẤT (master §2): nghe domain event AFTER_COMMIT, đọc dữ liệu
 * nguồn qua service công khai của module phát (matrix amend 2026-07-04), ghi in-app qua
 * {@link NotificationService} (REQUIRES_NEW) + email {@code @Async}. Mọi exception nuốt + log —
 * side-effect sau commit không được ném ngược.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private static final String REF_TRANSFER = "TICKET_TRANSFER";
    private static final String REF_EVENT = "EVENT";

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TransferService transferService;
    private final BookingService bookingService;
    private final EventService eventService;
    private final FollowService followService;

    /** P6 §1 — quá cap thì chỉ in-app, bỏ email (chống nghẽn; trade-off ghi trong plan). */
    @Value("${app.social.notify-email-max-followers:500}")
    private int notifyEmailMaxFollowers;

    // ---- transfer (P3 §1.2: notification 2 chiều + email) ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferChanged(TicketTransferChangedEvent event) {
        try {
            TicketTransfer transfer = transferService.loadWithDetails(event.transferId());
            String title = transfer.getBooking().getEvent().getTitle();
            User sender = transfer.getFromUser();
            User receiver = transfer.getToUser();
            String priceLine = transfer.getPrice() > 0
                    ? "Price: " + transfer.getPrice() + " VND (paid from your wallet on accept)."
                    : "This ticket is a gift — no payment needed.";

            switch (event.status()) {
                case PENDING -> {
                    notificationService.dispatch(NotificationType.TRANSFER_OFFER_RECEIVED, receiver,
                            "You received a ticket transfer offer",
                            sender.getFullName() + " wants to transfer you a ticket for \"" + title + "\". " + priceLine,
                            REF_TRANSFER, transfer.getPublicId());
                    emailService.sendNotificationEmail(receiver.getEmail(),
                            "Venvify — ticket transfer offer for \"" + title + "\"",
                            sender.getFullName() + " wants to transfer you a ticket for \"" + title + "\".\n"
                                    + priceLine + "\nOpen Venvify to accept or decline before it expires.");
                }
                case COMPLETED -> {
                    notificationService.dispatch(NotificationType.TRANSFER_COMPLETED, sender,
                            "Your ticket transfer was accepted",
                            receiver.getFullName() + " accepted your ticket for \"" + title + "\".",
                            REF_TRANSFER, transfer.getPublicId());
                    notificationService.dispatch(NotificationType.TRANSFER_COMPLETED, receiver,
                            "Ticket transferred to you",
                            "The ticket for \"" + title + "\" is now yours — see it in your bookings.",
                            REF_TRANSFER, transfer.getPublicId());
                    emailService.sendNotificationEmail(sender.getEmail(),
                            "Venvify — your ticket transfer was accepted",
                            receiver.getFullName() + " accepted your ticket for \"" + title + "\".");
                }
                case DECLINED -> notificationService.dispatch(NotificationType.TRANSFER_DECLINED, sender,
                        "Your ticket transfer was declined",
                        receiver.getFullName() + " declined your ticket for \"" + title + "\".",
                        REF_TRANSFER, transfer.getPublicId());
                case EXPIRED -> notificationService.dispatch(NotificationType.TRANSFER_EXPIRED, sender,
                        "Your ticket transfer expired",
                        "Your transfer offer for \"" + title + "\" expired without a response.",
                        REF_TRANSFER, transfer.getPublicId());
                case CANCELLED -> notificationService.dispatch(NotificationType.TRANSFER_CANCELLED, receiver,
                        "A ticket transfer was cancelled",
                        "The transfer offer for \"" + title + "\" is no longer available.",
                        REF_TRANSFER, transfer.getPublicId());
            }
        } catch (Exception e) {
            log.error("Failed to notify transfer change {} ({})", event.transferId(), event.status(), e);
        }
    }

    // ---- event hủy (P6 §4 takedown dùng chung): báo attendee kèm thông tin refund ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCancelled(EventCancelledEvent event) {
        try {
            Event cancelled = eventService.loadWithHost(event.eventId());
            List<Booking> bookings = bookingService.listForEventCancelNotice(event.eventId());

            List<Long> refunded = bookings.stream()
                    .filter(b -> b.getPricePaid() != null && b.getPricePaid() > 0)
                    .map(b -> b.getAttendee().getId()).toList();
            List<Long> free = bookings.stream()
                    .filter(b -> b.getPricePaid() == null || b.getPricePaid() == 0)
                    .map(b -> b.getAttendee().getId()).toList();

            notificationService.dispatchBatch(NotificationType.EVENT_CANCELLED, refunded,
                    "Event cancelled — you have been refunded",
                    "\"" + cancelled.getTitle() + "\" was cancelled. Your full ticket price was refunded to your wallet.",
                    REF_EVENT, cancelled.getPublicId());
            notificationService.dispatchBatch(NotificationType.EVENT_CANCELLED, free,
                    "Event cancelled",
                    "\"" + cancelled.getTitle() + "\" was cancelled by the host.",
                    REF_EVENT, cancelled.getPublicId());

            for (Booking booking : bookings) {
                boolean paid = booking.getPricePaid() != null && booking.getPricePaid() > 0;
                emailService.sendNotificationEmail(booking.getAttendee().getEmail(),
                        "Venvify — \"" + cancelled.getTitle() + "\" was cancelled",
                        paid
                                ? "\"" + cancelled.getTitle() + "\" was cancelled. Your full ticket price ("
                                        + booking.getPricePaid() + " VND) has been refunded to your Venvify wallet."
                                : "\"" + cancelled.getTitle() + "\" was cancelled by the host.");
            }
        } catch (Exception e) {
            log.error("Failed to notify cancellation of event {}", event.eventId(), e);
        }
    }

    // ---- event mới từ host đang follow (P6 §1): batch in-app + email có cap ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventPublished(EventPublishedEvent event) {
        try {
            Event published = eventService.loadWithHost(event.eventId());
            List<Long> followerIds = followService.listFollowerIds(published.getHost().getId());
            if (followerIds.isEmpty()) {
                return;
            }
            String hostName = published.getHost().getFullName();

            notificationService.dispatchBatch(NotificationType.NEW_EVENT_FROM_FOLLOWED_HOST, followerIds,
                    hostName + " just published a new event",
                    "\"" + published.getTitle() + "\" — check it out before slots run out.",
                    REF_EVENT, published.getPublicId());

            if (followerIds.size() > notifyEmailMaxFollowers) {
                log.info("Skipping follower emails for event {}: {} followers > cap {}",
                        published.getId(), followerIds.size(), notifyEmailMaxFollowers);
                return;
            }
            for (User follower : followService.listFollowers(published.getHost().getId())) {
                emailService.sendNotificationEmail(follower.getEmail(),
                        "Venvify — " + hostName + " published \"" + published.getTitle() + "\"",
                        hostName + " just published \"" + published.getTitle() + "\". Slots are limited — book early.");
            }
        } catch (Exception e) {
            log.error("Failed to fan out publish notification of event {}", event.eventId(), e);
        }
    }
}
