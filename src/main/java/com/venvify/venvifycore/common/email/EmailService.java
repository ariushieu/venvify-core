package com.venvify.venvifycore.common.email;

/** Trừu tượng gửi email (CLAUDE.md §3 — D: phụ thuộc abstraction). Impl hiện tại: Resend. */
public interface EmailService {

    void sendVerificationOtp(String toEmail, String fullName, String otp);
}
