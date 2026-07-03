package com.venvify.venvifycore.social.service;

import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.event.entity.Event;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.social.dto.CreateReviewRequest;
import com.venvify.venvifycore.social.dto.ReviewResponse;
import com.venvify.venvifycore.social.entity.Review;
import com.venvify.venvifycore.social.repository.ReviewRepository;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Review event (plan P6 §2). Gate ATTENDED giữ NGUYÊN dù data attendance chỉ có từ P4
 * — "ship dark" có chủ đích (amend log P6): thà chưa ai review được còn hơn nới xuống
 * CONFIRMED rồi nhận review ảo. User không sửa/xóa review (chống reputation gaming);
 * chỉ admin hide/unhide (slice admin).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingService bookingService;
    private final EventService eventService;
    private final UserService userService;

    /** P6 §2 — cửa sổ review kể từ khi event ENDED. */
    @Value("${app.social.review-window-days:14}")
    private long reviewWindowDays;

    @Transactional
    public ReviewResponse create(String reviewerPublicId, String eventPublicId, CreateReviewRequest request) {
        User reviewer = userService.getByPublicId(reviewerPublicId);
        Event event = eventService.loadByPublicId(eventPublicId);

        if (event.getStatus() != EventStatus.ENDED) {
            throw new BadRequestException("Only ended events can be reviewed");
        }
        if (event.getHost().getId().equals(reviewer.getId())) {
            throw new BadRequestException("You cannot review your own event");
        }
        // Cửa sổ tính từ end_time dự kiến — mốc ENDED thật không lưu riêng, chênh lệch
        // vài giờ của auto-end job chấp nhận được cho window 14 ngày.
        Instant windowEnd = event.getEndTime() == null
                ? null
                : event.getEndTime().plus(Duration.ofDays(reviewWindowDays));
        if (windowEnd != null && Instant.now().isAfter(windowEnd)) {
            throw new BadRequestException("Review window has closed for this event");
        }
        if (!bookingService.hasAttended(event.getId(), reviewer.getId())) {
            // Gồm cả NO_SHOW/CONFIRMED-chưa-điểm-danh: chỉ người tham dự thật được review.
            throw new ForbiddenException("Only attendees can review this event");
        }
        if (reviewRepository.existsByEventIdAndReviewerId(event.getId(), reviewer.getId())) {
            throw new ConflictException("You have already reviewed this event");
        }

        Review review = reviewRepository.save(Review.builder()
                .event(event)
                .reviewer(reviewer)
                .host(event.getHost())
                .rating(request.rating())
                .comment(request.comment())
                .build());
        return toResponse(review, false);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> listByEvent(String eventPublicId, int page, int size) {
        validatePage(page, size);
        Event event = eventService.loadByPublicId(eventPublicId);
        Pageable pageable = PageRequest.of(page, size);
        return PagedResponse.of(reviewRepository
                .findByEventIdAndHiddenFalseOrderByIdDesc(event.getId(), pageable)
                .map(r -> toResponse(r, false)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> listByHost(String handle, int page, int size) {
        validatePage(page, size);
        User host = userService.getActiveHostByHandle(handle);
        Pageable pageable = PageRequest.of(page, size);
        return PagedResponse.of(reviewRepository
                .findByHostIdAndHiddenFalseOrderByIdDesc(host.getId(), pageable)
                .map(r -> toResponse(r, true)));
    }

    /** Rating trung bình public (đã loại hidden) — analytics P6 §5. */
    @Transactional(readOnly = true)
    public double averageRatingForHost(Long hostId) {
        return reviewRepository.averageRatingByHostId(hostId);
    }

    /**
     * Moderation (P6 §2/§4) — CHỈ AdminModerationService gọi (bọc quyền ADMIN + audit cùng tx).
     * Idempotent: set trạng thái đích, không toggle.
     */
    @Transactional
    public Review setHidden(String reviewPublicId, boolean hidden) {
        Review review = reviewRepository.findByPublicId(reviewPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        review.setHidden(hidden);
        return reviewRepository.save(review);
    }

    // ----- helpers -----

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
    }

    private static ReviewResponse toResponse(Review review, boolean withEventTitle) {
        return new ReviewResponse(
                review.getPublicId(),
                review.getRating(),
                review.getComment(),
                review.getReviewer().getFullName(),
                review.getReviewer().getAvatarUrl(),
                withEventTitle ? review.getEvent().getTitle() : null,
                review.getCreatedAt());
    }
}
