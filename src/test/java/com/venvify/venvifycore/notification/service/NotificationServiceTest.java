package com.venvify.venvifycore.notification.service;

import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.notification.dto.NotificationResponse;
import com.venvify.venvifycore.notification.entity.Notification;
import com.venvify.venvifycore.notification.enums.NotificationType;
import com.venvify.venvifycore.notification.mapper.NotificationMapper;
import com.venvify.venvifycore.notification.repository.NotificationRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private UserService userService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private NotificationService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("u@venvify.com").fullName("U")
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        user.setId(5L);
        user.setPublicId("user-pid");
        lenient().when(userService.getByPublicId("user-pid")).thenReturn(user);
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(notificationMapper.toResponse(any())).thenReturn(
                new NotificationResponse("n-pid", NotificationType.TRANSFER_OFFER_RECEIVED,
                        "t", "c", false, null, null, null));
    }

    // ---- dispatch ----

    @Test
    void dispatch_persistsAllFields() {
        service.dispatch(NotificationType.TRANSFER_OFFER_RECEIVED, user,
                "Title", "Content", "TICKET_TRANSFER", "tt-pid");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getUser()).isSameAs(user);
        assertThat(n.getType()).isEqualTo(NotificationType.TRANSFER_OFFER_RECEIVED);
        assertThat(n.getTitle()).isEqualTo("Title");
        assertThat(n.getContent()).isEqualTo("Content");
        assertThat(n.isRead()).isFalse();
        assertThat(n.getRelatedEntityType()).isEqualTo("TICKET_TRANSFER");
        assertThat(n.getRelatedEntityPublicId()).isEqualTo("tt-pid");
    }

    @Test
    void dispatchBatch_emptyRecipients_noJdbcCall() {
        service.dispatchBatch(NotificationType.EVENT_CANCELLED, List.of(), "t", "c", "EVENT", "e-pid");

        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList(), anyInt(),
                any());
    }

    @Test
    void dispatchBatch_insertsOneRowPerRecipient() {
        service.dispatchBatch(NotificationType.EVENT_CANCELLED, List.of(1L, 2L, 3L),
                "t", "c", "EVENT", "e-pid");

        verify(jdbcTemplate).batchUpdate(anyString(), eq(List.of(1L, 2L, 3L)), eq(3),
                ArgumentMatchers.<ParameterizedPreparedStatementSetter<Long>>any());
    }

    // ---- listMine / markRead ----

    @Test
    void listMine_unreadOnly_usesUnreadQuery() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByIdDesc(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMine("user-pid", true, 0, 20);

        verify(notificationRepository).findByUserIdAndReadFalseOrderByIdDesc(eq(5L), any(Pageable.class));
        verify(notificationRepository, never()).findByUserIdOrderByIdDesc(any(), any());
    }

    @Test
    void listMine_sizeOverMax_rejected() {
        assertThatThrownBy(() -> service.listMine("user-pid", false, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void markRead_own_setsRead() {
        Notification n = Notification.builder().user(user)
                .type(NotificationType.TRANSFER_OFFER_RECEIVED).title("t").build();
        n.setPublicId("n-pid");
        when(notificationRepository.findByPublicId("n-pid")).thenReturn(Optional.of(n));

        service.markRead("user-pid", "n-pid");

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void markRead_othersNotification_hiddenAs404() {
        User other = User.builder().email("o@venvify.com").fullName("O")
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>()).build();
        other.setPublicId("other-pid");
        Notification n = Notification.builder().user(other)
                .type(NotificationType.TRANSFER_OFFER_RECEIVED).title("t").build();
        n.setPublicId("n-pid");
        when(notificationRepository.findByPublicId("n-pid")).thenReturn(Optional.of(n));

        // IDOR: 404 chứ không phải 403 — không xác nhận notification của người khác tồn tại.
        assertThatThrownBy(() -> service.markRead("user-pid", "n-pid"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void markAllRead_delegatesBulkUpdate() {
        when(notificationRepository.markAllRead(5L)).thenReturn(7);

        assertThat(service.markAllRead("user-pid")).isEqualTo(7);
    }

    @Test
    void unreadCount_delegates() {
        when(notificationRepository.countByUserIdAndReadFalse(5L)).thenReturn(3L);

        assertThat(service.unreadCount("user-pid")).isEqualTo(3);
    }
}
