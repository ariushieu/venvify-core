package com.venvify.venvifycore.social.repository;

import com.venvify.venvifycore.social.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByPublicId(String publicId);

    boolean existsByEventIdAndReviewerId(Long eventId, Long reviewerId);

    Page<Review> findByHostId(Long hostId, Pageable pageable);

    /** Rating trung bình của host cho storefront. */
    @Query("select coalesce(avg(r.rating), 0) from Review r where r.host.id = :hostId")
    double averageRatingByHostId(@Param("hostId") Long hostId);
}
