package com.venvify.venvifycore.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String EMAIL = "user@venvify.com";
    private static final int MAX_FAILURES = 3;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(MAX_FAILURES, 15);
    }

    @Test
    void isLocked_noFailures_returnsFalse() {
        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void isLocked_belowThreshold_returnsFalse() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);

        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void isLocked_atThreshold_returnsTrue() {
        for (int i = 0; i < MAX_FAILURES; i++) {
            service.recordFailure(EMAIL);
        }

        assertThat(service.isLocked(EMAIL)).isTrue();
    }

    @Test
    void clearFailures_resetsCounter() {
        for (int i = 0; i < MAX_FAILURES; i++) {
            service.recordFailure(EMAIL);
        }
        service.clearFailures(EMAIL);

        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void counter_isPerAccount() {
        for (int i = 0; i < MAX_FAILURES; i++) {
            service.recordFailure(EMAIL);
        }

        assertThat(service.isLocked("other@venvify.com")).isFalse();
    }

    @Test
    void email_isNormalizedCaseAndWhitespace() {
        for (int i = 0; i < MAX_FAILURES; i++) {
            service.recordFailure("  User@Venvify.COM ");
        }

        // Attacker đổi hoa/thường hoặc thêm space không né được counter.
        assertThat(service.isLocked(EMAIL)).isTrue();
    }
}
