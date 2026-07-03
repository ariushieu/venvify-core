package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.social.dto.FollowedHostResponse;
import com.venvify.venvifycore.social.entity.Follow;
import com.venvify.venvifycore.social.repository.FollowRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Follow host (plan P6 §1) — follow/unfollow idempotent (PUT/DELETE gọi lại không đổi kết quả);
 * UNIQUE(follower, host) là chốt chặn race. Hai method đọc cuối phục vụ NotificationListener.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserService userService;

    /** PUT /hosts/{handle}/follow — gọi lại khi đã follow = no-op. Không follow chính mình. */
    @Transactional
    public void follow(String userPublicId, String handle) {
        User follower = userService.getByPublicId(userPublicId);
        User host = userService.getActiveHostByHandle(handle);
        if (follower.getId().equals(host.getId())) {
            throw new BadRequestException("You cannot follow yourself");
        }
        if (followRepository.existsByFollowerIdAndHostId(follower.getId(), host.getId())) {
            return;
        }
        try {
            followRepository.save(Follow.builder().follower(follower).host(host).build());
        } catch (DataIntegrityViolationException e) {
            // 2 request follow song song — UNIQUE thắng, kết quả cuối vẫn "đã follow": idempotent.
        }
    }

    /** DELETE /hosts/{handle}/follow — chưa follow = no-op. */
    @Transactional
    public void unfollow(String userPublicId, String handle) {
        User follower = userService.getByPublicId(userPublicId);
        User host = userService.getActiveHostByHandle(handle);
        followRepository.deleteByFollowerIdAndHostId(follower.getId(), host.getId());
    }

    /** GET /users/me/following — mới follow gần nhất trước (R15: id DESC). */
    @Transactional(readOnly = true)
    public PagedResponse<FollowedHostResponse> listMyFollowing(String userPublicId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        User follower = userService.getByPublicId(userPublicId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PagedResponse.of(followRepository.findByFollowerId(follower.getId(), pageable)
                .map(f -> new FollowedHostResponse(
                        f.getHost().getHostHandle(),
                        f.getHost().getFullName(),
                        f.getHost().getAvatarUrl(),
                        f.getCreatedAt())));
    }

    /** Ids cho batch insert notification — không load cả entity. */
    @Transactional(readOnly = true)
    public List<Long> listFollowerIds(Long hostId) {
        return followRepository.findFollowerIdsByHostId(hostId);
    }

    /** Đếm follower cho storefront/analytics. */
    @Transactional(readOnly = true)
    public long countFollowers(Long hostId) {
        return followRepository.countByHostId(hostId);
    }

    /** Follower đầy đủ (email/tên) cho fan-out email — CHỈ gọi khi count ≤ cap (P6 §1). */
    @Transactional(readOnly = true)
    public List<User> listFollowers(Long hostId) {
        return followRepository.findFollowersByHostId(hostId);
    }
}
