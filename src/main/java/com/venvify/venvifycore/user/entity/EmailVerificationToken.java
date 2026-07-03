package com.venvify.venvifycore.user.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * OTP xác thực email — lưu HASH của mã 6 số, dùng một lần, có hạn.
 * KHÔNG unique trên hash: OTP ngắn nên hai user có thể trùng mã; tra cứu theo user.
 */
@Entity
@Table(
        name = "email_verification_tokens",
        indexes = @Index(name = "idx_evt_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex của mã OTP thật. */
    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    /** Số lần nhập sai — quá {@code AuthService.MAX_OTP_ATTEMPTS} thì mã bị vô hiệu. */
    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** NULL = chưa dùng; có giá trị = đã xác thực xong hoặc bị vô hiệu. */
    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
