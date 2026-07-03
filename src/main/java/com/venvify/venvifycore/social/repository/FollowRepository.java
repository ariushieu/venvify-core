package com.venvify.venvifycore.social.repository;

import com.venvify.venvifycore.social.entity.Follow;
import com.venvify.venvifycore.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndHostId(Long followerId, Long hostId);

    void deleteByFollowerIdAndHostId(Long followerId, Long hostId);

    /** Host tôi đang follow — fetch sẵn host cho DTO, sort ở service (R15: id DESC). */
    @EntityGraph(attributePaths = "host")
    Page<Follow> findByFollowerId(Long followerId, Pageable pageable);

    long countByHostId(Long hostId);

    /** Ids cho batch notification fan-out (P6 §1) — không kéo entity thừa. */
    @Query("select f.follower.id from Follow f where f.host.id = :hostId")
    List<Long> findFollowerIdsByHostId(@Param("hostId") Long hostId);

    /** Follower đầy đủ cho fan-out email — caller tự cap số lượng trước khi gọi. */
    @Query("select f.follower from Follow f where f.host.id = :hostId")
    List<User> findFollowersByHostId(@Param("hostId") Long hostId);
}
