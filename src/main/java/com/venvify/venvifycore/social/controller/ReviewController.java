package com.venvify.venvifycore.social.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.social.dto.CreateReviewRequest;
import com.venvify.venvifycore.social.dto.ReviewResponse;
import com.venvify.venvifycore.social.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Review event (plan P6 §2): tạo cần login + ATTENDED; đọc public, review hidden bị ẩn. */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/events/{eventPublicId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String eventPublicId,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse review = reviewService.create(userPublicId, eventPublicId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED.value(), "Review created", review));
    }

    @GetMapping("/events/{eventPublicId}/reviews")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> byEvent(
            @PathVariable String eventPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listByEvent(eventPublicId, page, size)));
    }

    @GetMapping("/hosts/{handle}/reviews")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> byHost(
            @PathVariable String handle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listByHost(handle, page, size)));
    }
}
