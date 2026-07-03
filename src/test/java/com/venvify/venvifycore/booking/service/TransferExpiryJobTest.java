package com.venvify.venvifycore.booking.service;

import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import com.venvify.venvifycore.booking.repository.TicketTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferExpiryJobTest {

    @Mock
    private TicketTransferRepository transferRepository;
    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferExpiryJob job;

    @Test
    void run_processesEachExpiredOfferIndependently() {
        when(transferRepository.findExpiredPendingIds(eq(TicketTransferStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        // Offer 2 lỗi — job phải log rồi đi tiếp, không chặn 3.
        doThrow(new RuntimeException("boom")).when(transferService).expireOne(2L);

        job.run();

        verify(transferService).expireOne(1L);
        verify(transferService).expireOne(2L);
        verify(transferService).expireOne(3L);
    }

    @Test
    void run_nothingExpired_noWork() {
        when(transferRepository.findExpiredPendingIds(eq(TicketTransferStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        job.run();

        verify(transferService, never()).expireOne(any());
    }
}
