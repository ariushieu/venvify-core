package com.venvify.venvifycore.user.service;

import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.mapper.UserMapper;
import com.venvify.venvifycore.user.repository.RefreshTokenRepository;
import com.venvify.venvifycore.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private static User host(UserStatus status) {
        return host(status, new HashSet<>());
    }

    private static User host(UserStatus status, Set<Role> roles) {
        User user = User.builder()
                .email("host@venvify.com")
                .fullName("Host")
                .hostHandle("host-handle")
                .status(status)
                .emailVerified(true)
                .roles(roles)
                .build();
        user.setId(7L);
        user.setPublicId("host-pid");
        return user;
    }

    @Test
    void getActiveHostByHandle_activeHost_returned() {
        when(userRepository.findByHostHandleAndDeletedFalse("host-handle"))
                .thenReturn(Optional.of(host(UserStatus.ACTIVE)));

        assertThat(userService.getActiveHostByHandle("host-handle").getId()).isEqualTo(7L);
    }

    @Test
    void getActiveHostByHandle_suspendedHost_hiddenAs404() {
        // Host bị ban không hiện storefront — 404 y như không tồn tại, không lộ trạng thái khóa.
        when(userRepository.findByHostHandleAndDeletedFalse("host-handle"))
                .thenReturn(Optional.of(host(UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> userService.getActiveHostByHandle("host-handle"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActiveHostByHandle_unknownHandle_404() {
        when(userRepository.findByHostHandleAndDeletedFalse("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getActiveHostByHandle("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByPublicId_unknown_404() {
        when(userRepository.findByPublicId("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByPublicId("x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- ban / unban (P6 §4) ----

    @Test
    void ban_activeUser_suspendsAndRevokesAllRefreshTokens() {
        User target = host(UserStatus.ACTIVE);
        when(userRepository.findByPublicId("host-pid")).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        User result = userService.ban("host-pid");

        assertThat(result.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        // Master §5: ban = revoke TOÀN BỘ refresh token — refresh cũ chết ngay.
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(7L), any(Instant.class));
    }

    @Test
    void ban_alreadySuspended_idempotentNoRevokeAgain() {
        User target = host(UserStatus.SUSPENDED);
        when(userRepository.findByPublicId("host-pid")).thenReturn(Optional.of(target));

        User result = userService.ban("host-pid");

        assertThat(result.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void ban_adminTarget_rejected() {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.ADMIN);
        User target = host(UserStatus.ACTIVE, roles);
        when(userRepository.findByPublicId("host-pid")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.ban("host-pid"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Administrators");
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void unban_suspended_reactivates() {
        User target = host(UserStatus.SUSPENDED);
        when(userRepository.findByPublicId("host-pid")).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        assertThat(userService.unban("host-pid").getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void unban_notSuspended_rejected() {
        User target = host(UserStatus.ACTIVE);
        when(userRepository.findByPublicId("host-pid")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.unban("host-pid"))
                .isInstanceOf(BadRequestException.class);
    }
}
