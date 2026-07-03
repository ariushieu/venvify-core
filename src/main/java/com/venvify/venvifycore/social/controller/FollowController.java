package com.venvify.venvifycore.social.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.social.dto.FollowedHostResponse;
import com.venvify.venvifycore.social.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Follow host (plan P6 §1) — PUT/DELETE idempotent, yêu cầu đăng nhập. */
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PutMapping("/hosts/{handle}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String handle) {
        followService.follow(userPublicId, handle);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Following"));
    }

    @DeleteMapping("/hosts/{handle}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String handle) {
        followService.unfollow(userPublicId, handle);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Unfollowed"));
    }

    @GetMapping("/users/me/following")
    public ResponseEntity<ApiResponse<PagedResponse<FollowedHostResponse>>> following(
            @AuthenticationPrincipal String userPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(followService.listMyFollowing(userPublicId, page, size)));
    }
}
