package com.venvify.venvifycore.event.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.event.dto.CategoryCountResponse;
import com.venvify.venvifycore.event.dto.EventCardResponse;
import com.venvify.venvifycore.event.dto.EventSearchQuery;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventListSort;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.mapper.EventMapper;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.event.repository.EventSearchRepository.IdPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDiscoveryServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventDiscoveryService service;

    private static EventSearchQuery query(int page, int size) {
        return new EventSearchQuery(null, null, null, null, null, EventListSort.UPCOMING, page, size);
    }

    private static Event event(long id) {
        Event event = Event.builder().title("E" + id).status(EventStatus.PUBLISHED).build();
        event.setId(id);
        return event;
    }

    private static EventCardResponse card(long id) {
        return new EventCardResponse("pid-" + id, "slug", "E" + id, null, null,
                null, null, null, 0L, 10, "h", "Host", null);
    }

    // ---- validate ----

    @Test
    void search_negativePage_rejected() {
        assertThatThrownBy(() -> service.search(query(-1, 20)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void search_sizeOverMax_rejected() {
        assertThatThrownBy(() -> service.search(query(0, 101)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100");
    }

    @Test
    void search_fromAfterTo_rejected() {
        Instant now = Instant.now();
        EventSearchQuery q = new EventSearchQuery(null, null, null,
                now.plusSeconds(60), now, EventListSort.UPCOMING, 0, 20);
        assertThatThrownBy(() -> service.search(q))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- search ----

    @Test
    void search_defaultsFromToNowTruncatedToMinute() {
        when(eventRepository.searchIds(any(), any())).thenReturn(new IdPage(List.of(), 0));

        service.search(query(0, 20));

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventRepository).searchIds(any(), fromCaptor.capture());
        Instant from = fromCaptor.getValue();
        assertThat(from).isEqualTo(from.truncatedTo(ChronoUnit.MINUTES));
        assertThat(from).isBetween(Instant.now().minusSeconds(120), Instant.now());
    }

    @Test
    void search_keepsIdOrderFromSearchPage() {
        // Bước 1 trả [5, 3] (đúng sort); IN (:ids) bước 2 trả lộn xộn [3, 5] — service phải xếp lại.
        when(eventRepository.searchIds(any(), any())).thenReturn(new IdPage(List.of(5L, 3L), 45));
        Event e3 = event(3);
        Event e5 = event(5);
        when(eventRepository.findWithHostByIdIn(List.of(5L, 3L))).thenReturn(List.of(e3, e5));
        when(eventMapper.toCard(e5)).thenReturn(card(5));
        when(eventMapper.toCard(e3)).thenReturn(card(3));

        PagedResponse<EventCardResponse> result = service.search(query(0, 20));

        assertThat(result.items()).extracting(EventCardResponse::publicId)
                .containsExactly("pid-5", "pid-3");
        assertThat(result.totalElements()).isEqualTo(45);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.last()).isFalse();
    }

    @Test
    void search_lastPageFlagAndEmptyResult() {
        when(eventRepository.searchIds(any(), any())).thenReturn(new IdPage(List.of(), 0));

        PagedResponse<EventCardResponse> result = service.search(query(0, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalPages()).isZero();
        assertThat(result.last()).isTrue();
        // Không được gọi load bước 2 với IN rỗng.
        verify(eventRepository, never()).findWithHostByIdIn(anyCollection());
    }

    @Test
    void search_honorsExplicitFrom() {
        Instant explicit = Instant.parse("2026-08-01T00:00:00Z");
        EventSearchQuery q = new EventSearchQuery(null, null, null,
                explicit, null, EventListSort.UPCOMING, 0, 20);
        when(eventRepository.searchIds(any(), any())).thenReturn(new IdPage(List.of(), 0));

        service.search(q);

        verify(eventRepository).searchIds(eq(q), eq(explicit));
    }

    // ---- categories ----

    @Test
    void countByCategory_fillsZeroForMissingCategories() {
        EventRepository.CategoryCount tech = new EventRepository.CategoryCount() {
            @Override
            public EventCategory getCategory() {
                return EventCategory.TECHNOLOGY;
            }

            @Override
            public long getTotal() {
                return 4;
            }
        };
        when(eventRepository.countUpcomingByCategory(eq(EventStatus.PUBLISHED), any()))
                .thenReturn(List.of(tech));

        List<CategoryCountResponse> result = service.countByCategory();

        assertThat(result).hasSize(EventCategory.values().length);
        assertThat(result).filteredOn(r -> r.category() == EventCategory.TECHNOLOGY)
                .singleElement()
                .satisfies(r -> assertThat(r.count()).isEqualTo(4));
        assertThat(result).filteredOn(r -> r.category() == EventCategory.HEALTH)
                .singleElement()
                .satisfies(r -> assertThat(r.count()).isZero());
    }
}
