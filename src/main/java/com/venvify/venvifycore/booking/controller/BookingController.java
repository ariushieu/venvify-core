package com.venvify.venvifycore.booking.controller;

import com.venvify.venvifycore.booking.dto.BookingResponse;
import com.venvify.venvifycore.booking.dto.CreateBookingRequest;
import com.venvify.venvifycore.booking.service.BookingService;
import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @AuthenticationPrincipal String userPublicId,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse booking = bookingService.create(userPublicId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED.value(), "Booking confirmed", booking));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PagedResponse<BookingResponse>>> mine(
            @AuthenticationPrincipal String userPublicId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.listMine(userPublicId, pageable)));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<BookingResponse>> detail(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.getDetail(userPublicId, publicId)));
    }

    @PatchMapping("/{publicId}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @AuthenticationPrincipal String userPublicId,
            @PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.cancel(userPublicId, publicId), "Booking cancelled"));
    }
}
