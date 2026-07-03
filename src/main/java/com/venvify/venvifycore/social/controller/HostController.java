package com.venvify.venvifycore.social.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.event.dto.EventCardResponse;
import com.venvify.venvifycore.event.enums.HostEventScope;
import com.venvify.venvifycore.social.dto.HostStorefrontResponse;
import com.venvify.venvifycore.social.service.HostStorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Storefront public theo vanity handle — không cần login (plan P3 §2.4). */
@RestController
@RequestMapping("/hosts")
@RequiredArgsConstructor
public class HostController {

    private final HostStorefrontService hostStorefrontService;

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<HostStorefrontResponse>> storefront(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.ok(hostStorefrontService.getStorefront(handle)));
    }

    @GetMapping("/{handle}/events")
    public ResponseEntity<ApiResponse<PagedResponse<EventCardResponse>>> events(
            @PathVariable String handle,
            @RequestParam(defaultValue = "UPCOMING") HostEventScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(hostStorefrontService.getHostEvents(handle, scope, page, size)));
    }
}
