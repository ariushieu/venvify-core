package com.venvify.venvifycore.booking.service;

import com.venvify.venvifycore.event.domain.EventCancelledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferEventListenerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferEventListener listener;

    @Test
    void onEventCancelled_sweepsPendingTransfers() {
        listener.onEventCancelled(new EventCancelledEvent(100L));

        verify(transferService).cancelPendingForEvent(100L);
    }

    @Test
    void onEventCancelled_failureIsSwallowed() {
        // Side-effect sau commit: không được ném ngược làm luồng cancel tưởng fail.
        doThrow(new RuntimeException("boom")).when(transferService).cancelPendingForEvent(100L);

        assertThatCode(() -> listener.onEventCancelled(new EventCancelledEvent(100L)))
                .doesNotThrowAnyException();
    }
}
