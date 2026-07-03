package com.venvify.venvifycore.user.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.user.dto.AuthResponse;
import com.venvify.venvifycore.user.dto.CreateUserRequest;
import com.venvify.venvifycore.user.dto.LoginRequest;
import com.venvify.venvifycore.user.dto.RefreshRequest;
import com.venvify.venvifycore.user.dto.ResendVerificationRequest;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.dto.VerifyEmailRequest;
import com.venvify.venvifycore.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                HttpStatus.CREATED.value(), "Registration successful. Please verify your email.", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Logged out"));
    }

    /** Nhập đúng OTP → xác thực email và trả luôn cặp token (auto sign-in). */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.verifyEmail(request.email(), request.otp()), "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.<Void>ok(null,
                "If the email exists and is unverified, a verification link has been sent."));
    }
}
