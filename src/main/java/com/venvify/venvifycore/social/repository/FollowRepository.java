package com.venvify.venvifycore.social.repository;

import com.venvify.venvifycore.social.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndHostId(Long followerId, Long hostId);

    void deleteByFollowerIdAndHostId(Long followerId, Long hostId);

    long countByHostId(Long hostId);
}
