package com.venvify.venvifycore.admin.controller;

import com.venvify.venvifycore.admin.dto.AdminDashboardResponse;
import com.venvify.venvifycore.admin.dto.ModerationRequest;
import com.venvify.venvifycore.admin.service.AdminDashboardService;
import com.venvify.venvifycore.admin.service.AdminModerationService;
import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.enums.EventStatus;
import com.venvify.venvifycore.event.service.EventService;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.service.UserService;
import com.venvify.venvifycore.wallet.dto.TransactionAdminResponse;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import com.venvify.venvifycore.wallet.service.TransactionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Admin panel API (plan P6 §4). AuthZ 2 lớp (master §5): matcher /admin/** hasRole(ADMIN)
 * ở SecurityConfig + @PreAuthorize class-level ở đây. Payout/suspense/AI-jobs chờ P2/P5
 * (amend log P6). Seed admin: SQL tay theo runbook — không hardcode migration.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final EventService eventService;
    private final AdminModerationService moderationService;
    private final AdminDashboardService dashboardService;
    private final TransactionQueryService transactionQueryService;

    // ---- users ----

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(userService.adminSearch(q, status, page, size)));
    }

    @PatchMapping("/users/{publicId}/ban")
    public ResponseEntity<ApiResponse<UserResponse>> ban(
            @AuthenticationPrincipal String adminPublicId,
            @PathVariable String publicId,
            @Valid @RequestBody(required = false) ModerationRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(
                moderationService.banUser(adminPublicId, publicId, reason), "User banned"));
    }

    @PatchMapping("/users/{publicId}/unban")
    public ResponseEntity<ApiResponse<UserResponse>> unban(
            @AuthenticationPrincipal String adminPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(
                moderationService.unbanUser(adminPublicId, publicId), "User unbanned"));
    }

    // ---- events ----

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<PagedResponse<EventResponse>>> events(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.adminSearch(q, status, page, size)));
    }

    @PostMapping("/events/{publicId}/takedown")
    public ResponseEntity<ApiResponse<EventResponse>> takedown(
            @AuthenticationPrincipal String adminPublicId,
            @PathVariable String publicId,
            @Valid @RequestBody(required = false) ModerationRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(
                moderationService.takedownEvent(adminPublicId, publicId, reason), "Event taken down"));
    }

    // ---- reviews (moderation P6 §2) ----

    @PatchMapping("/reviews/{publicId}/hide")
    public ResponseEntity<ApiResponse<Void>> hideReview(
            @AuthenticationPrincipal String adminPublicId,
            @PathVariable String publicId,
            @Valid @RequestBody(required = false) ModerationRequest request) {
        moderationService.hideReview(adminPublicId, publicId, request == null ? null : request.reason());
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Review hidden"));
    }

    @PatchMapping("/reviews/{publicId}/unhide")
    public ResponseEntity<ApiResponse<Void>> unhideReview(
            @AuthenticationPrincipal String adminPublicId,
            @PathVariable String publicId) {
        moderationService.unhideReview(adminPublicId, publicId);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Review unhidden"));
    }

    // ---- transactions (đọc-only, CSKH/đối soát) ----

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionAdminResponse>>> transactions(
            @RequestParam(required = false) String ref,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String userPublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                transactionQueryService.adminSearch(ref, type, userPublicId, from, to, page, size)));
    }

    // ---- KPI ----

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.dashboard()));
    }
}
