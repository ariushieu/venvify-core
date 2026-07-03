package com.venvify.venvifycore.social.repository;

import com.venvify.venvifycore.social.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByPublicId(String publicId);

    /** UNIQUE(event, reviewer) — kể cả review đã hidden vẫn chặn review lại (chống spam vòng). */
    boolean existsByEventIdAndReviewerId(Long eventId, Long reviewerId);

    @EntityGraph(attributePaths = "reviewer")
    Page<Review> findByEventIdAndHiddenFalseOrderByIdDesc(Long eventId, Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "event"})
    Page<Review> findByHostIdAndHiddenFalseOrderByIdDesc(Long hostId, Pageable pageable);

    /** Rating trung bình public của host — review hidden không tính. */
    @Query("select coalesce(avg(r.rating), 0) from Review r where r.host.id = :hostId and r.hidden = false")
    double averageRatingByHostId(@Param("hostId") Long hostId);

    long countByHostIdAndHiddenFalse(Long hostId);
}
