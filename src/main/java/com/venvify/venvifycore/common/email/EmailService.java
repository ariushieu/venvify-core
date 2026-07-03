package com.venvify.venvifycore.common.email;

/** Trừu tượng gửi email (CLAUDE.md §3 — D: phụ thuộc abstraction). Impl hiện tại: Resend. */
public interface EmailService {

    void sendVerificationOtp(String toEmail, String fullName, String otp);

    /**
     * Báo động vận hành P0 tới admin ({@code app.ops.admin-email}) — vd reconcile lệch tiền
     * (money-core §4). Impl không được ném exception (job phải chạy tiếp) nhưng phải log ERROR
     * nếu gửi thất bại.
     */
    void sendOpsAlert(String subject, String body);
}
