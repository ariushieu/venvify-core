package com.venvify.venvifycore.booking.controller;

import com.venvify.venvifycore.booking.dto.CreateTransferRequest;
import com.venvify.venvifycore.booking.dto.TransferResponse;
import com.venvify.venvifycore.booking.enums.TicketTransferStatus;
import com.venvify.venvifycore.booking.enums.TransferRole;
import com.venvify.venvifycore.booking.service.TransferService;
import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chuyển nhượng vé (plan P3 §1.5) — toàn bộ yêu cầu đăng nhập. */
@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/bookings/{bookingPublicId}/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> create(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String bookingPublicId,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferResponse transfer = transferService.createOffer(userPublicId, bookingPublicId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED.value(), "Transfer offer created", transfer));
    }

    @GetMapping("/transfers/mine")
    public ResponseEntity<ApiResponse<PagedResponse<TransferResponse>>> mine(
            @AuthenticationPrincipal String userPublicId,
            @RequestParam(defaultValue = "RECEIVED") TransferRole role,
            @RequestParam(required = false) TicketTransferStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.listMine(userPublicId, role, status, page, size)));
    }

    @PostMapping("/transfers/{publicId}/accept")
    public ResponseEntity<ApiResponse<TransferResponse>> accept(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.accept(userPublicId, publicId), "Transfer completed"));
    }

    @PostMapping("/transfers/{publicId}/decline")
    public ResponseEntity<ApiResponse<TransferResponse>> decline(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.decline(userPublicId, publicId), "Transfer declined"));
    }

    @DeleteMapping("/transfers/{publicId}")
    public ResponseEntity<ApiResponse<TransferResponse>> cancel(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.cancel(userPublicId, publicId), "Transfer cancelled"));
    }
}
