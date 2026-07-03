package com.venvify.venvifycore.analytics.controller;

import com.venvify.venvifycore.analytics.dto.EventStatsResponse;
import com.venvify.venvifycore.analytics.dto.HostStatsResponse;
import com.venvify.venvifycore.analytics.service.HostAnalyticsService;
import com.venvify.venvifycore.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Host analytics (plan P6 §5) — yêu cầu đăng nhập; event-stats chỉ host của event. */
@RestController
@RequiredArgsConstructor
public class HostAnalyticsController {

    private final HostAnalyticsService hostAnalyticsService;

    @GetMapping("/users/me/host-stats")
    public ResponseEntity<ApiResponse<HostStatsResponse>> hostStats(
            @AuthenticationPrincipal String userPublicId) {
        return ResponseEntity.ok(ApiResponse.ok(hostAnalyticsService.hostStats(userPublicId)));
    }

    @GetMapping("/events/{publicId}/stats")
    public ResponseEntity<ApiResponse<EventStatsResponse>> eventStats(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(hostAnalyticsService.eventStats(userPublicId, publicId)));
    }
}
