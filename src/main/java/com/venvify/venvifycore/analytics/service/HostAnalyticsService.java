package com.venvify.venvifycore.analytics.service;

import com.venvify.venvifycore.analytics.dto.EventStatsResponse;
import com.venvify.venvifycore.analytics.dto.HostStatsResponse;
import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.interaction.service.InteractionQueryService;
import com.venvify.venvifycore.social.service.FollowService;
import com.venvify.venvifycore.social.service.ReviewService;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.service.TransactionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Host analytics (plan P6 §5) — thuần SELECT trên data sẵn, đọc mọi module qua service
 * công khai (ma trận §2, dòng analytics — amend 2026-07-04). Không pre-aggregate:
 * tầm nghìn dòng query trực tiếp đủ nhanh.
 */
@Service
@RequiredArgsConstructor
public class HostAnalyticsService {

    private final UserService userService;
    private final EventService eventService;
    private final BookingService bookingService;
    private final TransactionQueryService transactionQueryService;
    private final FollowService followService;
    private final ReviewService reviewService;
    private final InteractionQueryService interactionQueryService;

    /** Tổng quan host — user chưa từng publish thì mọi số đều 0, không cần chặn role. */
    @Transactional(readOnly = true)
    public HostStatsResponse hostStats(String userPublicId) {
        User host = userService.getByPublicId(userPublicId);
        return new HostStatsResponse(
                eventService.countByHost(host.getId()),
                bookingService.countAttendeesForHost(host.getId()),
                transactionQueryService.releasedHostNetForHost(host.getId()),
                followService.countFollowers(host.getId()),
                reviewService.averageRatingForHost(host.getId()));
    }

    /** Thống kê 1 event — CHỈ host của event đó xem được (P6 §5). */
    @Transactional(readOnly = true)
    public EventStatsResponse eventStats(String userPublicId, String eventPublicId) {
        Event event = eventService.loadByPublicId(eventPublicId);
        if (!event.getHost().getPublicId().equals(userPublicId)) {
            throw new ForbiddenException("Only the event host can view its stats");
        }
        TransactionQueryService.EventRevenue revenue = transactionQueryService.revenueForEvent(event.getId());
        return new EventStatsResponse(
                event.getPublicId(),
                bookingService.countsByStatusForEvent(event.getId()),
                revenue.grossSold(),
                revenue.hostNetReleased(),
                interactionQueryService.countPolls(event.getId()),
                interactionQueryService.countPollVotes(event.getId()),
                interactionQueryService.countQuestions(event.getId()));
    }
}
