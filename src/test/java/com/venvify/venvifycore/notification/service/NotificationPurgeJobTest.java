package com.venvify.venvifycore.notification.service;

import com.venvify.venvifycore.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPurgeJobTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationPurgeJob job;

    @Test
    void run_deletesOlderThanRetention() {
        when(notificationRepository.deleteOlderThan(any())).thenReturn(4);

        job.run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteOlderThan(cutoff.capture());
        Instant expected = Instant.now().minus(NotificationPurgeJob.RETENTION);
        assertThat(cutoff.getValue()).isBetween(expected.minusSeconds(60), expected.plusSeconds(60));
    }
}
