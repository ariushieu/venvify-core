package com.venvify.venvifycore.user.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Đếm login sai theo account để chặn brute-force (T6, architecture §5/§15).
 * Quá {@code login-max-failures} lần trong cửa sổ lockout → khóa login {@code login-lockout-minutes}
 * phút. Phía ngoài LUÔN trả 401 generic — không lộ account đang bị khóa (chính sách account-lifecycle).
 *
 * <p>State nằm in-process (Caffeine) — đủ cho 1 instance hiện tại; restart là reset, chấp nhận được
 * vì đây là rate-limit chứ không phải audit. Khi scale nhiều instance thì chuyển state này ra ngoài.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final Cache<String, Integer> failures;

    public LoginAttemptService(
            @Value("${app.auth.login-max-failures:10}") int maxFailures,
            @Value("${app.auth.login-lockout-minutes:15}") long lockoutMinutes) {
        this.maxFailures = maxFailures;
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(lockoutMinutes))
                .maximumSize(100_000)
                .build();
    }

    /** true = account đang bị khóa vì sai quá ngưỡng trong cửa sổ lockout. */
    public boolean isLocked(String email) {
        Integer count = failures.getIfPresent(normalize(email));
        return count != null && count >= maxFailures;
    }

    /** Ghi 1 lần sai. Mỗi lần ghi reset TTL → attacker càng thử, khóa càng kéo dài. */
    public void recordFailure(String email) {
        failures.asMap().merge(normalize(email), 1, Integer::sum);
    }

    /** Đúng mật khẩu → xóa counter (chuỗi sai trước đó không còn ý nghĩa). */
    public void clearFailures(String email) {
        failures.invalidate(normalize(email));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
