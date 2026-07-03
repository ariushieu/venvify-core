package com.venvify.venvifycore.admin.service;

import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.social.entity.Review;
import com.venvify.venvifycore.social.service.ReviewService;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.mapper.UserMapper;
import com.venvify.venvifycore.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private AuditService auditService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminModerationService service;

    private User admin;
    private User target;

    @BeforeEach
    void setUp() {
        admin = user(1L, "admin-pid");
        target = user(2L, "target-pid");
        lenient().when(userService.getByPublicId("admin-pid")).thenReturn(admin);
    }

    private static User user(long id, String publicId) {
        User u = User.builder()
                .email(publicId + "@venvify.com").fullName("U" + id)
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        u.setId(id);
        u.setPublicId(publicId);
        return u;
    }

    @Test
    void banUser_delegatesAndAuditsInSameFlow() {
        when(userService.ban("target-pid")).thenReturn(target);

        service.banUser("admin-pid", "target-pid", "spam");

        verify(userService).ban("target-pid");
        // Audit CÙNG tx với mutation (AuditService MANDATORY enforce ở runtime).
        verify(auditService).record(eq(admin), eq("USER_BAN"), eq("USER"), eq("target-pid"), eq("spam"));
    }

    @Test
    void banUser_mutationFails_noAuditRow() {
        when(userService.ban("target-pid")).thenThrow(new BadRequestException("Administrators cannot be banned"));

        assertThatThrownBy(() -> service.banUser("admin-pid", "target-pid", null))
                .isInstanceOf(BadRequestException.class);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void takedown_reusesAdminCancelAndAudits() {
        EventResponse cancelled = mock(EventResponse.class);
        when(eventService.cancelAsAdmin("evt-pid")).thenReturn(cancelled);

        service.takedownEvent("admin-pid", "evt-pid", "scam event");

        verify(eventService).cancelAsAdmin("evt-pid");
        verify(auditService).record(eq(admin), eq("EVENT_TAKEDOWN"), eq("EVENT"), eq("evt-pid"), eq("scam event"));
    }

    @Test
    void hideReview_setsFlagAndAudits() {
        Review review = Review.builder().build();
        review.setPublicId("rv-pid");
        when(reviewService.setHidden("rv-pid", true)).thenReturn(review);

        service.hideReview("admin-pid", "rv-pid", "toxic");

        verify(reviewService).setHidden("rv-pid", true);
        verify(auditService).record(eq(admin), eq("REVIEW_HIDE"), eq("REVIEW"), eq("rv-pid"), eq("toxic"));
    }

    @Test
    void unbanUser_audits() {
        when(userService.unban("target-pid")).thenReturn(target);

        service.unbanUser("admin-pid", "target-pid");

        verify(auditService).record(eq(admin), eq("USER_UNBAN"), eq("USER"), eq("target-pid"), eq(null));
    }
}
