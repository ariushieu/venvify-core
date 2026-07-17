package com.venvify.venvifycore.user.service;

import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.UnauthorizedException;
import com.venvify.venvifycore.user.entity.EmailVerificationToken;
import com.venvify.venvifycore.user.entity.RefreshToken;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.repository.EmailVerificationTokenRepository;
import com.venvify.venvifycore.user.repository.RefreshTokenRepository;
import com.venvify.venvifycore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra qua Spring transaction proxy + MySQL thật. Mockito unit test không quan sát được
 * commit/rollback nên không thể chặn regression của reuse detection và OTP attempt persistence.
 */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AuthServiceTransactionTest {

    private static final String OTP = "123456";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final JdbcTemplate jdbcTemplate;

    private Long createdUserId;

    @AfterEach
    void cleanCreatedUser() {
        if (createdUserId == null) {
            return;
        }
        jdbcTemplate.update("delete from refresh_tokens where user_id = ?", createdUserId);
        jdbcTemplate.update("delete from email_verification_tokens where user_id = ?", createdUserId);
        jdbcTemplate.update("delete from user_roles where user_id = ?", createdUserId);
        jdbcTemplate.update("delete from users where id = ?", createdUserId);
    }

    @Test
    void refresh_reusedToken_commitsRevocationOfOtherSessions() {
        User user = createUnverifiedUser();
        String reusedRaw = "reused-" + UUID.randomUUID();
        String activeRaw = "active-" + UUID.randomUUID();
        Instant now = Instant.now();

        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(reusedRaw))
                .expiresAt(now.plus(1, ChronoUnit.DAYS))
                .revokedAt(now.minus(1, ChronoUnit.MINUTES))
                .build());
        RefreshToken active = refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(activeRaw))
                .expiresAt(now.plus(1, ChronoUnit.DAYS))
                .build());

        assertThatThrownBy(() -> authService.refresh(reusedRaw))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("reuse");

        RefreshToken persisted = refreshTokenRepository.findById(active.getId()).orElseThrow();
        assertThat(persisted.getRevokedAt()).isNotNull();
        assertThat(countRefreshTokens(user.getId())).isEqualTo(2);
    }

    @Test
    void verifyEmail_wrongOtp_commitsAttempt() {
        User user = createUnverifiedUser();
        EmailVerificationToken token = createOtp(user);

        assertThatThrownBy(() -> authService.verifyEmail(user.getEmail(), "000000"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Incorrect");

        EmailVerificationToken persisted = emailVerificationTokenRepository
                .findById(token.getId()).orElseThrow();
        assertThat(persisted.getAttempts()).isEqualTo(1);
        assertThat(persisted.getUsedAt()).isNull();
    }

    @Test
    void verifyEmail_fifthWrongOtp_commitsLock() {
        User user = createUnverifiedUser();
        EmailVerificationToken token = createOtp(user);

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> authService.verifyEmail(user.getEmail(), "000000"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        EmailVerificationToken persisted = emailVerificationTokenRepository
                .findById(token.getId()).orElseThrow();
        assertThat(persisted.getAttempts()).isEqualTo(5);
        assertThat(persisted.getUsedAt()).isNotNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();

        assertThatThrownBy(() -> authService.verifyEmail(user.getEmail(), OTP))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_correctOtpAfterFourFailures_commitsVerificationAndToken() {
        User user = createUnverifiedUser();
        EmailVerificationToken token = createOtp(user);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> authService.verifyEmail(user.getEmail(), "000000"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        authService.verifyEmail(user.getEmail(), OTP);

        EmailVerificationToken persisted = emailVerificationTokenRepository
                .findById(token.getId()).orElseThrow();
        assertThat(persisted.getAttempts()).isEqualTo(4);
        assertThat(persisted.getUsedAt()).isNotNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        assertThat(countRefreshTokens(user.getId())).isEqualTo(1);
    }

    @Test
    void verifyEmail_twoConcurrentCorrectRequests_issueOnlyOneSession() throws Exception {
        User user = createUnverifiedUser();
        createOtp(user);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> verifyAfterSignal(start, user.getEmail()));
            Future<Boolean> second = executor.submit(() -> verifyAfterSignal(start, user.getEmail()));
            start.countDown();

            long successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(successes).isEqualTo(1);
            assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
            assertThat(countRefreshTokens(user.getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void verifyEmail_tenConcurrentWrongRequests_lockAtFiveAttempts() throws Exception {
        User user = createUnverifiedUser();
        EmailVerificationToken token = createOtp(user);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                requests.add(executor.submit(() -> wrongOtpAfterSignal(start, user.getEmail())));
            }
            start.countDown();

            for (Future<Boolean> request : requests) {
                assertThat(request.get(10, TimeUnit.SECONDS)).isTrue();
            }

            EmailVerificationToken persisted = emailVerificationTokenRepository
                    .findById(token.getId()).orElseThrow();
            assertThat(persisted.getAttempts()).isEqualTo(5);
            assertThat(persisted.getUsedAt()).isNotNull();
            assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean verifyAfterSignal(CountDownLatch start, String email) throws InterruptedException {
        start.await();
        try {
            authService.verifyEmail(email, OTP);
            return true;
        } catch (UnauthorizedException | ConflictException expected) {
            return false;
        }
    }

    private boolean wrongOtpAfterSignal(CountDownLatch start, String email) throws InterruptedException {
        start.await();
        try {
            authService.verifyEmail(email, "000000");
            return false;
        } catch (UnauthorizedException expected) {
            return true;
        }
    }

    private User createUnverifiedUser() {
        User user = userRepository.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@example.test")
                .passwordHash("integration-test-hash")
                .fullName("Auth Transaction Test")
                .emailVerified(false)
                .status(UserStatus.ACTIVE)
                .roles(Set.of(Role.ATTENDEE))
                .build());
        createdUserId = user.getId();
        return user;
    }

    private EmailVerificationToken createOtp(User user) {
        return emailVerificationTokenRepository.saveAndFlush(EmailVerificationToken.builder()
                .user(user)
                .otpHash(sha256(OTP))
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build());
    }

    private long countRefreshTokens(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
