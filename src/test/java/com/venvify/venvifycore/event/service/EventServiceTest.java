package com.venvify.venvifycore.event.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.event.dto.CreateEventRequest;
import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.dto.UpdateEventRequest;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.enums.EventTimezone;
import com.venvify.venvifycore.event.mapper.EventMapper;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EventService eventService;

    private static final String OWNER_PID = "owner-pid";
    private static final EventResponse RESPONSE = new EventResponse(
            "evt-pid", OWNER_PID, "host", "Title", "title", "desc", EventCategory.TECHNOLOGY,
            null, null, null, 10, 0, 0L, EventStatus.DRAFT, null, null);

    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .email("owner@venvify.com")
                .fullName("Owner")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>())
                .build();
        owner.setId(1L);
        owner.setPublicId(OWNER_PID);
    }

    private Event event(EventStatus status) {
        Event event = Event.builder()
                .host(owner)
                .title("Title")
                .slug("title")
                .maxSlots(10)
                .claimedSlots(0)
                .priceAmount(0L)
                .status(status)
                .build();
        event.setId(100L);
        event.setPublicId("evt-pid");
        return event;
    }

    // ---- create ----

    @Test
    void create_grantsHostRoleAndPersistsDraft() {
        CreateEventRequest request = new CreateEventRequest(
                "My Talk", "desc", EventCategory.TECHNOLOGY, null, null, null, 10, 0L);
        Event mapped = new Event();
        when(userRepository.findByPublicId(OWNER_PID)).thenReturn(Optional.of(owner));
        when(eventMapper.toEntity(request)).thenReturn(mapped);
        when(eventRepository.save(mapped)).thenReturn(mapped);
        when(eventMapper.toResponse(mapped)).thenReturn(RESPONSE);

        EventResponse result = eventService.create(OWNER_PID, request);

        assertThat(result).isSameAs(RESPONSE);
        assertThat(owner.getRoles()).contains(Role.HOST);
        verify(userRepository).save(owner);
        assertThat(mapped.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(mapped.getHost()).isSameAs(owner);
        assertThat(mapped.getClaimedSlots()).isZero();
        assertThat(mapped.getSlug()).isEqualTo("my-talk");
    }

    @Test
    void create_doesNotResaveUserWhenAlreadyHost() {
        owner.getRoles().add(Role.HOST);
        CreateEventRequest request = new CreateEventRequest(
                "Talk", null, null, null, null, null, 5, 0L);
        Event mapped = new Event();
        when(userRepository.findByPublicId(OWNER_PID)).thenReturn(Optional.of(owner));
        when(eventMapper.toEntity(request)).thenReturn(mapped);
        when(eventRepository.save(mapped)).thenReturn(mapped);
        when(eventMapper.toResponse(mapped)).thenReturn(RESPONSE);

        eventService.create(OWNER_PID, request);

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_appendsSuffixWhenSlugCollides() {
        CreateEventRequest request = new CreateEventRequest(
                "Talk", null, null, null, null, null, 5, 0L);
        Event mapped = new Event();
        when(userRepository.findByPublicId(OWNER_PID)).thenReturn(Optional.of(owner));
        when(eventMapper.toEntity(request)).thenReturn(mapped);
        when(eventRepository.existsBySlug("talk")).thenReturn(true);
        when(eventRepository.existsBySlug(org.mockito.ArgumentMatchers.startsWith("talk-"))).thenReturn(false);
        when(eventRepository.save(mapped)).thenReturn(mapped);
        when(eventMapper.toResponse(mapped)).thenReturn(RESPONSE);

        eventService.create(OWNER_PID, request);

        assertThat(mapped.getSlug()).startsWith("talk-");
        assertThat(mapped.getSlug()).isNotEqualTo("talk");
    }

    @Test
    void create_userNotFound_throwsNotFound() {
        CreateEventRequest request = new CreateEventRequest(
                "Talk", null, null, null, null, null, 5, 0L);
        when(userRepository.findByPublicId(OWNER_PID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.create(OWNER_PID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withTimezone_persistsDraft() {
        CreateEventRequest request = new CreateEventRequest(
                "Talk", null, null, null, null, EventTimezone.VIETNAM, 5, 0L);
        Event mapped = new Event();
        when(userRepository.findByPublicId(OWNER_PID)).thenReturn(Optional.of(owner));
        when(eventMapper.toEntity(request)).thenReturn(mapped);
        when(eventRepository.save(mapped)).thenReturn(mapped);
        when(eventMapper.toResponse(mapped)).thenReturn(RESPONSE);

        assertThat(eventService.create(OWNER_PID, request)).isSameAs(RESPONSE);
    }

    // ---- update ----

    @Test
    void update_byNonOwner_throwsForbidden() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.DRAFT)));
        UpdateEventRequest request = new UpdateEventRequest(
                "New", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> eventService.update("stranger", "evt-pid", request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_endedEvent_throwsBadRequest() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.ENDED)));
        UpdateEventRequest request = new UpdateEventRequest(
                "New", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> eventService.update(OWNER_PID, "evt-pid", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_publishedEvent_rejectsPriceChange() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));
        UpdateEventRequest request = new UpdateEventRequest(
                null, null, null, null, null, null, null, 5000L, null);

        assertThatThrownBy(() -> eventService.update(OWNER_PID, "evt-pid", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Price");
    }

    @Test
    void update_publishedEvent_rejectsLoweringMaxSlotsBelowClaimed() {
        Event published = event(EventStatus.PUBLISHED);
        published.setClaimedSlots(4);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(published));
        UpdateEventRequest request = new UpdateEventRequest(
                null, null, null, null, null, null, 3, null, null);

        assertThatThrownBy(() -> eventService.update(OWNER_PID, "evt-pid", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_publishedEvent_rejectsReschedule() {
        Event published = event(EventStatus.PUBLISHED);
        published.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(published));
        UpdateEventRequest request = new UpdateEventRequest(
                null, null, null, Instant.now().plus(3, ChronoUnit.DAYS), null, null, null, null, null);

        assertThatThrownBy(() -> eventService.update(OWNER_PID, "evt-pid", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Reschedul");
    }

    @Test
    void update_draftEvent_appliesChangesAndSaves() {
        Event draft = event(EventStatus.DRAFT);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));
        when(eventRepository.save(draft)).thenReturn(draft);
        when(eventMapper.toResponse(draft)).thenReturn(RESPONSE);
        UpdateEventRequest request = new UpdateEventRequest(
                "New title", "new", EventCategory.EDUCATION, null, null, null, 20, null, null);

        EventResponse result = eventService.update(OWNER_PID, "evt-pid", request);

        assertThat(result).isSameAs(RESPONSE);
        verify(eventMapper).updateEntity(request, draft);
        verify(eventRepository).save(draft);
    }

    // ---- publish ----

    @Test
    void publish_nonDraft_throwsBadRequest() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));

        assertThatThrownBy(() -> eventService.publish(OWNER_PID, "evt-pid"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void publish_missingTimeOrTimezone_throwsBadRequest() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        assertThatThrownBy(() -> eventService.publish(OWNER_PID, "evt-pid"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("required");
    }

    @Test
    void publish_startInPast_throwsBadRequest() {
        Event draft = event(EventStatus.DRAFT);
        draft.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        draft.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));
        draft.setTimezone(EventTimezone.VIETNAM);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> eventService.publish(OWNER_PID, "evt-pid"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    void publish_endBeforeStart_throwsBadRequest() {
        Event draft = event(EventStatus.DRAFT);
        draft.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        draft.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS));
        draft.setTimezone(EventTimezone.VIETNAM);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> eventService.publish(OWNER_PID, "evt-pid"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void publish_valid_setsPublished() {
        Event draft = event(EventStatus.DRAFT);
        draft.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        draft.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        draft.setTimezone(EventTimezone.VIETNAM);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));
        when(eventRepository.save(draft)).thenReturn(draft);
        when(eventMapper.toResponse(draft)).thenReturn(RESPONSE);

        eventService.publish(OWNER_PID, "evt-pid");

        assertThat(draft.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    // ---- cancel / delete ----

    @Test
    void cancel_publishedEvent_setsCancelled() {
        Event published = event(EventStatus.PUBLISHED);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(published));
        when(eventRepository.save(published)).thenReturn(published);
        when(eventMapper.toResponse(published)).thenReturn(RESPONSE);

        eventService.cancel(OWNER_PID, "evt-pid");

        assertThat(published.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancel_endedEvent_throwsBadRequest() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.ENDED)));

        assertThatThrownBy(() -> eventService.cancel(OWNER_PID, "evt-pid"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_draftEvent_setsDeletedFlag() {
        Event draft = event(EventStatus.DRAFT);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));
        when(eventRepository.save(draft)).thenReturn(draft);

        eventService.delete(OWNER_PID, "evt-pid");

        assertThat(draft.isDeleted()).isTrue();
        verify(eventRepository).save(draft);
    }

    @Test
    void delete_publishedEvent_throwsBadRequest() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));

        assertThatThrownBy(() -> eventService.delete(OWNER_PID, "evt-pid"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_alreadyDeletedEvent_throwsNotFound() {
        Event draft = event(EventStatus.DRAFT);
        draft.setDeleted(true);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> eventService.delete(OWNER_PID, "evt-pid"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- detail / list ----

    @Test
    void getDetail_draftByNonOwner_throwsNotFound() {
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        assertThatThrownBy(() -> eventService.getDetail("stranger", "evt-pid"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDetail_draftByOwner_returnsResponse() {
        Event draft = event(EventStatus.DRAFT);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(draft));
        when(eventMapper.toResponse(draft)).thenReturn(RESPONSE);

        assertThat(eventService.getDetail(OWNER_PID, "evt-pid")).isSameAs(RESPONSE);
    }

    @Test
    void getDetail_publishedByGuest_returnsResponse() {
        Event published = event(EventStatus.PUBLISHED);
        when(eventRepository.findByPublicId("evt-pid")).thenReturn(Optional.of(published));
        when(eventMapper.toResponse(published)).thenReturn(RESPONSE);

        assertThat(eventService.getDetail(null, "evt-pid")).isSameAs(RESPONSE);
    }

    @Test
    void listPublished_withoutCategory_usesStatusQuery() {
        Event published = event(EventStatus.PUBLISHED);
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findByStatusAndDeletedFalse(eq(EventStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(published)));
        when(eventMapper.toResponse(published)).thenReturn(RESPONSE);

        PagedResponse<EventResponse> result = eventService.listPublished(null, pageable);

        assertThat(result.items()).containsExactly(RESPONSE);
        verify(eventRepository, never())
                .findByStatusAndCategoryAndDeletedFalse(any(), any(), any());
    }

    @Test
    void listPublished_withCategory_usesCategoryQuery() {
        Event published = event(EventStatus.PUBLISHED);
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findByStatusAndCategoryAndDeletedFalse(
                eq(EventStatus.PUBLISHED), eq(EventCategory.TECHNOLOGY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(published)));
        when(eventMapper.toResponse(published)).thenReturn(RESPONSE);

        PagedResponse<EventResponse> result = eventService.listPublished(EventCategory.TECHNOLOGY, pageable);

        assertThat(result.items()).containsExactly(RESPONSE);
    }

    @Test
    void listMine_returnsHostEvents() {
        Event draft = event(EventStatus.DRAFT);
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findByPublicId(OWNER_PID)).thenReturn(Optional.of(owner));
        when(eventRepository.findByHostIdAndDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draft)));
        when(eventMapper.toResponse(draft)).thenReturn(RESPONSE);

        PagedResponse<EventResponse> result = eventService.listMine(OWNER_PID, pageable);

        assertThat(result.items()).containsExactly(RESPONSE);
    }
}
