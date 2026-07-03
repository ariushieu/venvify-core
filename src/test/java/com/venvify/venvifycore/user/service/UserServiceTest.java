package com.venvify.venvifycore.user.service;

import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static User host(UserStatus status) {
        User user = User.builder()
                .email("host@venvify.com")
                .fullName("Host")
                .hostHandle("host-handle")
                .status(status)
                .emailVerified(true)
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
}
