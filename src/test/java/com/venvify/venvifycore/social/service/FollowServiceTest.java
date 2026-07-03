package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.social.dto.FollowedHostResponse;
import com.venvify.venvifycore.social.entity.Follow;
import com.venvify.venvifycore.social.repository.FollowRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private FollowService followService;

    private User follower;
    private User host;

    @BeforeEach
    void setUp() {
        follower = user(1L, "follower-pid", null);
        host = user(2L, "host-pid", "host-handle");
        lenient().when(userService.getByPublicId("follower-pid")).thenReturn(follower);
        lenient().when(userService.getActiveHostByHandle("host-handle")).thenReturn(host);
    }

    private static User user(long id, String publicId, String handle) {
        User u = User.builder()
                .email(publicId + "@venvify.com").fullName("User " + id).hostHandle(handle)
                .status(UserStatus.ACTIVE).emailVerified(true).roles(new HashSet<>())
                .build();
        u.setId(id);
        u.setPublicId(publicId);
        return u;
    }

    @Test
    void follow_firstTime_saves() {
        when(followRepository.existsByFollowerIdAndHostId(1L, 2L)).thenReturn(false);

        followService.follow("follower-pid", "host-handle");

        verify(followRepository).save(any(Follow.class));
    }

    @Test
    void follow_alreadyFollowing_idempotentNoop() {
        when(followRepository.existsByFollowerIdAndHostId(1L, 2L)).thenReturn(true);

        followService.follow("follower-pid", "host-handle");

        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_raceDuplicate_swallowedAsIdempotent() {
        when(followRepository.existsByFollowerIdAndHostId(1L, 2L)).thenReturn(false);
        when(followRepository.save(any(Follow.class)))
                .thenThrow(new DataIntegrityViolationException("uq_follow_follower_host"));

        // 2 request song song: UNIQUE thắng, kết quả cuối vẫn "đã follow" — không lộ lỗi 500.
        assertThatCode(() -> followService.follow("follower-pid", "host-handle"))
                .doesNotThrowAnyException();
    }

    @Test
    void follow_self_rejected() {
        when(userService.getByPublicId("host-owner")).thenReturn(host);

        assertThatThrownBy(() -> followService.follow("host-owner", "host-handle"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void unfollow_deletesQuietly() {
        followService.unfollow("follower-pid", "host-handle");

        verify(followRepository).deleteByFollowerIdAndHostId(1L, 2L);
    }

    @Test
    void listMyFollowing_mapsHostFields() {
        Follow follow = Follow.builder().follower(follower).host(host).build();
        when(followRepository.findByFollowerId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(follow)));

        PagedResponse<FollowedHostResponse> result =
                followService.listMyFollowing("follower-pid", 0, 20);

        assertThat(result.items()).singleElement().satisfies(r -> {
            assertThat(r.handle()).isEqualTo("host-handle");
            assertThat(r.name()).isEqualTo("User 2");
        });
    }
}
