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
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingMapper bookingMapper;

    @InjectMocks
    private BookingService bookingService;

    private static final String ATTENDEE_PID = "attendee-pid";
    private static final String HOST_PID = "host-pid";
    private static final String EVENT_PID = "evt-pid";
    private static final BookingResponse RESPONSE = new BookingResponse(
            "bk-pid", EVENT_PID, "Title", BookingStatus.CONFIRMED, 0L, null);

    private User attendee;
    private User host;

    @BeforeEach
    void setUp() {
        attendee = user(2L, ATTENDEE_PID, "attendee@venvify.com");
        host = user(1L, HOST_PID, "host@venvify.com");
    }

    private User user(long id, String publicId, String email) {
        User u = User.builder()
                .email(email)
                .fullName("User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>())
                .build();
        u.setId(id);
        u.setPublicId(publicId);
        return u;
    }

    private Event event(EventStatus status, long price, int max, int claimed) {
        Event e = Event.builder()
                .host(host)
                .title("Title")
                .slug("title")
                .maxSlots(max)
                .claimedSlots(claimed)
                .priceAmount(price)
                .status(status)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        e.setId(100L);
        e.setPublicId(EVENT_PID);
        return e;
    }

    private Booking booking(BookingStatus status, Event event) {
        Booking b = Booking.builder()
                .event(event)
                .attendee(attendee)
                .status(status)
                .pricePaid(0L)
                .bookedAt(Instant.now())
                .build();
        b.setId(500L);
        b.setPublicId("bk-pid");
        return b;
    }

    // ---- create ----

    @Test
    void create_freeEvent_confirmsAndIncrementsSlot() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 3);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));
        when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.empty());
        when(eventRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(event));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingMapper.toResponse(any(Booking.class))).thenReturn(RESPONSE);

        BookingResponse result = bookingService.create(ATTENDEE_PID, request);

        assertThat(result).isSameAs(RESPONSE);
        assertThat(event.getClaimedSlots()).isEqualTo(4);
        verify(eventRepository).save(event);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.getPricePaid()).isZero();
        assertThat(saved.getBookedAt()).isNotNull();
        assertThat(saved.getEvent()).isSameAs(event);
        assertThat(saved.getAttendee()).isSameAs(attendee);
    }

    @Test
    void create_cancelledBooking_isReactivated() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 0);
        Booking existing = booking(BookingStatus.CANCELLED, event);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));
        when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.of(existing));
        when(eventRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(event));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingMapper.toResponse(any(Booking.class))).thenReturn(RESPONSE);

        bookingService.create(ATTENDEE_PID, request);

        assertThat(event.getClaimedSlots()).isEqualTo(1);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void create_paidEvent_throwsBadRequest() {
        Event event = event(EventStatus.PUBLISHED, 50_000L, 10, 0);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> bookingService.create(ATTENDEE_PID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Paid");
        verify(eventRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void create_nonPublishedEvent_throwsBadRequest() {
        Event event = event(EventStatus.DRAFT, 0L, 10, 0);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> bookingService.create(ATTENDEE_PID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_ownEvent_throwsBadRequest() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 0);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(HOST_PID)).thenReturn(Optional.of(host));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> bookingService.create(HOST_PID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own event");
    }

    @Test
    void create_duplicateActiveBooking_throwsConflict() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking existing = booking(BookingStatus.CONFIRMED, event);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));
        when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> bookingService.create(ATTENDEE_PID, request))
                .isInstanceOf(ConflictException.class);
        verify(eventRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void create_soldOut_throwsBadRequest() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 10);
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.of(event));
        when(bookingRepository.findByEventIdAndAttendeeId(100L, 2L)).thenReturn(Optional.empty());
        when(eventRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> bookingService.create(ATTENDEE_PID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sold out");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_eventNotFound_throwsNotFound() {
        CreateBookingRequest request = new CreateBookingRequest(EVENT_PID);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(eventRepository.findByPublicId(EVENT_PID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(ATTENDEE_PID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- listMine ----

    @Test
    void listMine_returnsAttendeeBookings() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking b = booking(BookingStatus.CONFIRMED, event);
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findByPublicId(ATTENDEE_PID)).thenReturn(Optional.of(attendee));
        when(bookingRepository.findByAttendeeId(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(b)));
        when(bookingMapper.toResponse(b)).thenReturn(RESPONSE);

        PagedResponse<BookingResponse> result = bookingService.listMine(ATTENDEE_PID, pageable);

        assertThat(result.items()).containsExactly(RESPONSE);
    }

    // ---- getDetail ----

    @Test
    void getDetail_byAttendee_returnsResponse() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking b = booking(BookingStatus.CONFIRMED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));
        when(bookingMapper.toResponse(b)).thenReturn(RESPONSE);

        assertThat(bookingService.getDetail(ATTENDEE_PID, "bk-pid")).isSameAs(RESPONSE);
    }

    @Test
    void getDetail_byHost_returnsResponse() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking b = booking(BookingStatus.CONFIRMED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));
        when(bookingMapper.toResponse(b)).thenReturn(RESPONSE);

        assertThat(bookingService.getDetail(HOST_PID, "bk-pid")).isSameAs(RESPONSE);
    }

    @Test
    void getDetail_byStranger_throwsForbidden() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking b = booking(BookingStatus.CONFIRMED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bookingService.getDetail("stranger", "bk-pid"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- cancel ----

    @Test
    void cancel_byNonOwner_throwsForbidden() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking b = booking(BookingStatus.CONFIRMED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bookingService.cancel(HOST_PID, "bk-pid"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_alreadyCancelled_throwsBadRequest() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        Booking b = booking(BookingStatus.CANCELLED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bookingService.cancel(ATTENDEE_PID, "bk-pid"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancel_afterEventStarted_throwsBadRequest() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 1);
        event.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        Booking b = booking(BookingStatus.CONFIRMED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bookingService.cancel(ATTENDEE_PID, "bk-pid"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("started");
    }

    @Test
    void cancel_valid_decrementsSlotAndCancels() {
        Event event = event(EventStatus.PUBLISHED, 0L, 10, 5);
        Booking b = booking(BookingStatus.CONFIRMED, event);
        when(bookingRepository.findByPublicId("bk-pid")).thenReturn(Optional.of(b));
        when(eventRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(bookingRepository.save(b)).thenReturn(b);
        when(bookingMapper.toResponse(b)).thenReturn(RESPONSE);

        bookingService.cancel(ATTENDEE_PID, "bk-pid");

        assertThat(event.getClaimedSlots()).isEqualTo(4);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
