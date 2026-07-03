package com.venvify.venvifycore.analytics.service;

import com.venvify.venvifycore.analytics.dto.EventStatsResponse;
import com.venvify.venvifycore.analytics.dto.HostStatsResponse;
import com.venvify.venvifycore.booking.enums.BookingStatus;
import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.interaction.service.InteractionQueryService;
import com.venvify.venvifycore.social.service.FollowService;
import com.venvify.venvifycore.social.service.ReviewService;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.service.TransactionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostAnalyticsServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;
    @Mock
    private BookingService bookingService;
    @Mock
    private TransactionQueryService transactionQueryService;
    @Mock
    private FollowService followService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private InteractionQueryService interactionQueryService;

    @InjectMocks
    private HostAnalyticsService service;

    private User host;
    private Event event;

    @BeforeEach
    void setUp() {
        host = User.builder()
                .email("host@venvify.com").fullName("Host")
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        host.setId(9L);
        host.setPublicId("host-pid");

        event = Event.builder()
                .host(host).title("Title").slug("title")
                .maxSlots(10).claimedSlots(5).priceAmount(100_000L)
                .status(EventStatus.ENDED)
                .build();
        event.setId(100L);
        event.setPublicId("evt-pid");

        lenient().when(userService.getByPublicId("host-pid")).thenReturn(host);
        lenient().when(eventService.loadByPublicId("evt-pid")).thenReturn(event);
    }

    @Test
    void hostStats_aggregatesAcrossModules() {
        when(eventService.countByHost(9L)).thenReturn(4L);
        when(bookingService.countAttendeesForHost(9L)).thenReturn(37L);
        when(transactionQueryService.releasedHostNetForHost(9L)).thenReturn(1_900_000L);
        when(followService.countFollowers(9L)).thenReturn(12L);
        when(reviewService.averageRatingForHost(9L)).thenReturn(4.5);

        HostStatsResponse result = service.hostStats("host-pid");

        assertThat(result.totalEvents()).isEqualTo(4);
        assertThat(result.totalAttendees()).isEqualTo(37);
        assertThat(result.releasedRevenue()).isEqualTo(1_900_000L);
        assertThat(result.followerCount()).isEqualTo(12);
        assertThat(result.avgRating()).isEqualTo(4.5);
    }

    @Test
    void eventStats_ownHostOnly() {
        when(bookingService.countsByStatusForEvent(100L))
                .thenReturn(Map.of(BookingStatus.CONFIRMED, 5L));
        when(transactionQueryService.revenueForEvent(100L))
                .thenReturn(new TransactionQueryService.EventRevenue(500_000L, 380_000L));
        when(interactionQueryService.countPolls(100L)).thenReturn(2L);
        when(interactionQueryService.countPollVotes(100L)).thenReturn(9L);
        when(interactionQueryService.countQuestions(100L)).thenReturn(6L);

        EventStatsResponse result = service.eventStats("host-pid", "evt-pid");

        assertThat(result.bookingsByStatus()).containsEntry(BookingStatus.CONFIRMED, 5L);
        assertThat(result.grossRevenue()).isEqualTo(500_000L);
        assertThat(result.hostNetReleased()).isEqualTo(380_000L);
        assertThat(result.pollCount()).isEqualTo(2);
        assertThat(result.pollVoteCount()).isEqualTo(9);
        assertThat(result.questionCount()).isEqualTo(6);
    }

    @Test
    void eventStats_notTheHost_forbidden() {
        assertThatThrownBy(() -> service.eventStats("someone-else", "evt-pid"))
                .isInstanceOf(ForbiddenException.class);
    }
}
