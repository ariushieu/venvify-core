package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.event.dto.EventCardResponse;
import com.venvify.venvifycore.event.enums.HostEventScope;
import com.venvify.venvifycore.event.service.EventDiscoveryService;
import com.venvify.venvifycore.social.dto.HostStorefrontResponse;
import com.venvify.venvifycore.social.repository.FollowRepository;
import com.venvify.venvifycore.social.repository.ReviewRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storefront public của host (plan P3 §2.4). Đọc chéo module qua service (ma trận §2);
 * follow/review count là repo NHÀ social. Profile cache 60s — follower/rating trễ tối đa
 * 1 phút, chấp nhận; tab events không cache (đổi theo publish/cancel realtime hơn).
 */
@Service
@RequiredArgsConstructor
public class HostStorefrontService {

    private final UserService userService;
    private final EventDiscoveryService eventDiscoveryService;
    private final FollowRepository followRepository;
    private final ReviewRepository reviewRepository;

    @Cacheable(cacheNames = "hostStorefront", key = "#handle")
    @Transactional(readOnly = true)
    public HostStorefrontResponse getStorefront(String handle) {
        User host = userService.getActiveHostByHandle(handle);
        return new HostStorefrontResponse(
                host.getHostHandle(),
                host.getFullName(),
                host.getAvatarUrl(),
                host.getBio(),
                followRepository.countByHostId(host.getId()),
                reviewRepository.countByHostIdAndHiddenFalse(host.getId()),
                reviewRepository.averageRatingByHostId(host.getId()),
                eventDiscoveryService.countUpcomingByHost(host.getId()));
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventCardResponse> getHostEvents(String handle, HostEventScope scope, int page, int size) {
        User host = userService.getActiveHostByHandle(handle);
        return eventDiscoveryService.hostEvents(host.getId(), scope, page, size);
    }
}
