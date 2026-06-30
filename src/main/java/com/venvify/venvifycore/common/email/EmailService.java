package com.venvify.venvifycore.common.email;

/** Trừu tượng gửi email (CLAUDE.md §3 — D: phụ thuộc abstraction). Impl hiện tại: Resend. */
public interface EmailService {

    void sendVerificationEmail(String toEmail, String fullName, String verificationLink);
}
