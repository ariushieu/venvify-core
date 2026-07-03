package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.social.repository.FollowRepository;
import com.venvify.venvifycore.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Follow host (plan P6 §1). Endpoint follow/unfollow đắp thêm ở slice follow;
 * hai method đọc dưới phục vụ NotificationListener fan-out event mới.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;

    /** Ids cho batch insert notification — không load cả entity. */
    @Transactional(readOnly = true)
    public List<Long> listFollowerIds(Long hostId) {
        return followRepository.findFollowerIdsByHostId(hostId);
    }

    /** Follower đầy đủ (email/tên) cho fan-out email — CHỈ gọi khi count ≤ cap (P6 §1). */
    @Transactional(readOnly = true)
    public List<User> listFollowers(Long hostId) {
        return followRepository.findFollowersByHostId(hostId);
    }
}
