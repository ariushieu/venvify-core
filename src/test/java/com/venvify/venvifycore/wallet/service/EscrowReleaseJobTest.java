package com.venvify.venvifycore.wallet.service;

import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.repository.EventRepository;
import com.venvify.venvifycore.wallet.enums.EscrowStatus;
import com.venvify.venvifycore.wallet.repository.EscrowHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscrowReleaseJobTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EscrowHoldRepository escrowHoldRepository;
    @Mock
    private EscrowService escrowService;

    @InjectMocks
    private EscrowReleaseJob job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "releaseDelayDays", 3);
        // lenient: từng test override stub findReleasableHoldIds — strict stubs sẽ báo unused.
        lenient().when(eventRepository.markEnded(eq(EventStatus.ENDED),
                eq(List.of(EventStatus.PUBLISHED, EventStatus.LIVE)), any(Instant.class)))
                .thenReturn(0);
        lenient().when(escrowHoldRepository.findReleasableHoldIds(
                eq(EscrowStatus.HELD), eq(EventStatus.ENDED), any(Instant.class)))
                .thenReturn(List.of());
    }

    @Test
    void run_autoEndsExpiredEventsFirst() {
        job.run();

        // Transition ENDED đầu tiên của hệ thống nằm ở đây (money-core §3.4).
        verify(eventRepository).markEnded(eq(EventStatus.ENDED),
                eq(List.of(EventStatus.PUBLISHED, EventStatus.LIVE)), any(Instant.class));
    }

    @Test
    void run_queriesHoldsWithDisputeWindowCutoff() {
        job.run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(escrowHoldRepository).findReleasableHoldIds(
                eq(EscrowStatus.HELD), eq(EventStatus.ENDED), cutoff.capture());
        // O-M3: chỉ release hold của event kết thúc TRƯỚC now − 3 ngày.
        Instant expected = Instant.now().minus(3, ChronoUnit.DAYS);
        assertThat(Duration.between(expected, cutoff.getValue()).abs())
                .isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void run_releasesEachEligibleHold() {
        when(escrowHoldRepository.findReleasableHoldIds(
                eq(EscrowStatus.HELD), eq(EventStatus.ENDED), any(Instant.class)))
                .thenReturn(List.of(1L, 2L, 3L));

        job.run();

        verify(escrowService).releaseHold(1L);
        verify(escrowService).releaseHold(2L);
        verify(escrowService).releaseHold(3L);
    }

    @Test
    void run_holdFailure_doesNotStopRemainingHolds() {
        when(escrowHoldRepository.findReleasableHoldIds(
                eq(EscrowStatus.HELD), eq(EventStatus.ENDED), any(Instant.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("optimistic lock")).when(escrowService).releaseHold(2L);

        job.run();

        // Hold lỗi nằm lại HELD, lần sau thử tiếp; hold sau nó vẫn được xử lý.
        verify(escrowService).releaseHold(3L);
    }

    @Test
    void run_noEligibleHolds_movesNoMoney() {
        job.run();

        verify(escrowService, never()).releaseHold(anyLong());
    }
}
