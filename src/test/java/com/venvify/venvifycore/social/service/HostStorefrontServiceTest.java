package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.event.dto.EventCardResponse;
import com.venvify.venvifycore.event.enums.HostEventScope;
import com.venvify.venvifycore.event.service.EventDiscoveryService;
import com.venvify.venvifycore.social.dto.HostStorefrontResponse;
import com.venvify.venvifycore.social.repository.FollowRepository;
import com.venvify.venvifycore.social.repository.ReviewRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostStorefrontServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private EventDiscoveryService eventDiscoveryService;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private HostStorefrontService service;

    private User host;

    @BeforeEach
    void setUp() {
        host = User.builder()
                .email("host@venvify.com")
                .fullName("Host Name")
                .hostHandle("host-handle")
                .avatarUrl("http://a/x.png")
                .bio("bio")
                .status(UserStatus.ACTIVE)
                .build();
        host.setId(7L);
        lenient().when(userService.getActiveHostByHandle("host-handle")).thenReturn(host);
    }

    @Test
    void getStorefront_aggregatesCounts() {
        when(followRepository.countByHostId(7L)).thenReturn(12L);
        when(reviewRepository.countByHostIdAndHiddenFalse(7L)).thenReturn(5L);
        when(reviewRepository.averageRatingByHostId(7L)).thenReturn(4.4);
        when(eventDiscoveryService.countUpcomingByHost(7L)).thenReturn(3L);

        HostStorefrontResponse result = service.getStorefront("host-handle");

        assertThat(result.handle()).isEqualTo("host-handle");
        assertThat(result.name()).isEqualTo("Host Name");
        assertThat(result.followerCount()).isEqualTo(12);
        assertThat(result.reviewCount()).isEqualTo(5);
        assertThat(result.avgRating()).isEqualTo(4.4);
        assertThat(result.upcomingEventCount()).isEqualTo(3);
    }

    @Test
    void getStorefront_unknownHandle_propagates404() {
        when(userService.getActiveHostByHandle("nope"))
                .thenThrow(new ResourceNotFoundException("Host not found"));

        assertThatThrownBy(() -> service.getStorefront("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHostEvents_delegatesWithHostId() {
        PagedResponse<EventCardResponse> page = new PagedResponse<>(List.of(), 0, 20, 0, 0, true);
        when(eventDiscoveryService.hostEvents(7L, HostEventScope.PAST, 0, 20)).thenReturn(page);

        assertThat(service.getHostEvents("host-handle", HostEventScope.PAST, 0, 20)).isSameAs(page);
    }
}
