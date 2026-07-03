package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.social.dto.CreateReviewRequest;
import com.venvify.venvifycore.social.dto.ReviewResponse;
import com.venvify.venvifycore.social.entity.Review;
import com.venvify.venvifycore.social.repository.ReviewRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private EventService eventService;
    @Mock
    private UserService userService;

    @InjectMocks
    private ReviewService reviewService;

    private User reviewer;
    private User host;
    private Event event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewService, "reviewWindowDays", 14L);

        reviewer = user(1L, "reviewer-pid");
        host = user(9L, "host-pid");
        event = Event.builder()
                .host(host).title("Title").slug("title")
                .maxSlots(10).claimedSlots(5).priceAmount(0L)
                .status(EventStatus.ENDED)
                .startTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        event.setId(100L);
        event.setPublicId("evt-pid");

        lenient().when(userService.getByPublicId("reviewer-pid")).thenReturn(reviewer);
        lenient().when(eventService.loadByPublicId("evt-pid")).thenReturn(event);
        lenient().when(bookingService.hasAttended(100L, 1L)).thenReturn(true);
        lenient().when(reviewRepository.existsByEventIdAndReviewerId(100L, 1L)).thenReturn(false);
        lenient().when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
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

    private static CreateReviewRequest request() {
        return new CreateReviewRequest((short) 5, "Great event");
    }

    // ---- eligibility matrix (P6 §2) ----

    @Test
    void create_attendedWithinWindow_saves() {
        ReviewResponse response = reviewService.create("reviewer-pid", "evt-pid", request());

        assertThat(response.rating()).isEqualTo((short) 5);
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getHost()).isSameAs(host);
        assertThat(saved.getValue().isHidden()).isFalse();
    }

    @Test
    void create_eventNotEnded_rejected() {
        event.setStatus(EventStatus.PUBLISHED);

        assertThatThrownBy(() -> reviewService.create("reviewer-pid", "evt-pid", request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ended");
    }

    @Test
    void create_windowClosed_rejected() {
        event.setEndTime(Instant.now().minus(15, ChronoUnit.DAYS));

        assertThatThrownBy(() -> reviewService.create("reviewer-pid", "evt-pid", request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("window");
    }

    @Test
    void create_notAttended_forbidden() {
        // NO_SHOW / CONFIRMED chưa điểm danh đều rơi vào đây — gate ATTENDED (ship dark tới P4).
        when(bookingService.hasAttended(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.create("reviewer-pid", "evt-pid", request()))
                .isInstanceOf(ForbiddenException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void create_duplicate_conflict() {
        when(reviewRepository.existsByEventIdAndReviewerId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create("reviewer-pid", "evt-pid", request()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_ownEvent_rejected() {
        when(userService.getByPublicId("host-pid")).thenReturn(host);

        assertThatThrownBy(() -> reviewService.create("host-pid", "evt-pid", request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own event");
    }

    // ---- public listing ----

    @Test
    void listByEvent_mapsWithoutEventTitle() {
        Review review = Review.builder().event(event).reviewer(reviewer).host(host)
                .rating((short) 4).comment("ok").build();
        review.setPublicId("rv-pid");
        when(reviewRepository.findByEventIdAndHiddenFalseOrderByIdDesc(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));

        var result = reviewService.listByEvent("evt-pid", 0, 20);

        assertThat(result.items()).singleElement().satisfies(r -> {
            assertThat(r.publicId()).isEqualTo("rv-pid");
            assertThat(r.eventTitle()).isNull();
            assertThat(r.reviewerName()).isEqualTo("User 1");
        });
    }

    @Test
    void listByHost_includesEventTitle() {
        when(userService.getActiveHostByHandle("host-handle")).thenReturn(host);
        Review review = Review.builder().event(event).reviewer(reviewer).host(host)
                .rating((short) 4).comment("ok").build();
        when(reviewRepository.findByHostIdAndHiddenFalseOrderByIdDesc(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));

        var result = reviewService.listByHost("host-handle", 0, 20);

        assertThat(result.items()).singleElement()
                .satisfies(r -> assertThat(r.eventTitle()).isEqualTo("Title"));
    }
}
