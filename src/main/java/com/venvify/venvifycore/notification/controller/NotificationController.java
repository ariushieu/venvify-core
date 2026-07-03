package com.venvify.venvifycore.notification.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.notification.dto.NotificationResponse;
import com.venvify.venvifycore.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Notification in-app (plan P6 §3). FE poll unread-count mỗi 30s — đủ MVP. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal String userPublicId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.listMine(userPublicId, unreadOnly, page, size)));
    }

    @PatchMapping("/{publicId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markRead(userPublicId, publicId)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> readAll(
            @AuthenticationPrincipal String userPublicId) {
        int updated = notificationService.markAllRead(userPublicId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", updated)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal String userPublicId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.unreadCount(userPublicId))));
    }
}
