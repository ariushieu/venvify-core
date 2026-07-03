package com.venvify.venvifycore.user.service;

import com.venvify.venvifycore.common.email.EmailService;
import com.venvify.venvifycore.common.exception.ConflictException;
import com.venvify.venvifycore.common.exception.ForbiddenException;
import com.venvify.venvifycore.common.exception.UnauthorizedException;
import com.venvify.venvifycore.common.security.JwtTokenProvider;
import com.venvify.venvifycore.user.dto.AuthResponse;
import com.venvify.venvifycore.user.dto.CreateUserRequest;
import com.venvify.venvifycore.user.dto.LoginRequest;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.entity.EmailVerificationToken;
import com.venvify.venvifycore.user.entity.RefreshToken;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.mapper.UserMapper;
import com.venvify.venvifycore.user.repository.EmailVerificationTokenRepository;
import com.venvify.venvifycore.user.repository.RefreshTokenRepository;
import com.venvify.venvifycore.user.repository.UserRepository;
import com.venvify.venvifycore.wallet.entity.Wallet;
import com.venvify.venvifycore.wallet.enums.WalletAccountType;
import com.venvify.venvifycore.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private EmailService emailService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private static final String EMAIL = "user@venvify.com";
    private static final String RAW_PASSWORD = "Password1";
    private static final String PASSWORD_HASH = "$2a$hashed";
    private static final UserResponse USER_RESPONSE = new UserResponse(
            "user-pid", EMAIL, "Full Name", null, null, null,
            false, UserStatus.ACTIVE, Set.of(Role.ATTENDEE), Instant.now());

    @BeforeEach
    void setUp() {
        // @Value fields aren't injected by @InjectMocks — set realistic positive TTLs.
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(authService, "verificationTokenExpirationMs", 600_000L);
    }

    private User activeUser() {
        User user = User.builder()
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .fullName("Full Name")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
        user.setId(1L);
        user.setPublicId("user-pid");
        return user;
    }

    private RefreshToken refreshToken(User user, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
        token.setId(10L);
        return token;
    }

    // ---- register ----

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);
        CreateUserRequest request = new CreateUserRequest(EMAIL, RAW_PASSWORD, "Full Name");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(emailService, never()).sendVerificationOtp(anyString(), anyString(), anyString());
    }

    @Test
    void register_newEmail_persistsUserWalletAndSendsVerification() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userMapper.toResponse(any(User.class))).thenReturn(USER_RESPONSE);
        CreateUserRequest request = new CreateUserRequest(EMAIL, RAW_PASSWORD, "Full Name");

        UserResponse result = authService.register(request);

        assertThat(result).isSameAs(USER_RESPONSE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getRoles()).containsExactly(Role.ATTENDEE);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        Wallet wallet = walletCaptor.getValue();
        assertThat(wallet.getAccountType()).isEqualTo(WalletAccountType.USER);
        assertThat(wallet.getUser()).isSameAs(saved);
        assertThat(wallet.getCurrency()).isEqualTo("VND");
        assertThat(wallet.getBalanceCached()).isZero();

        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationOtp(eq(EMAIL), eq("Full Name"), anyString());
    }

    // ---- login ----

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_oauthOnlyUserWithoutPassword_throwsUnauthorized() {
        User oauthUser = activeUser();
        oauthUser.setPasswordHash(null);
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);
        // Null hash must short-circuit before hitting the encoder.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_emailNotVerified_throwsForbidden() {
        User unverified = activeUser();
        unverified.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(unverified));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("verified");
    }

    @Test
    void login_suspendedAccount_throwsForbidden() {
        User suspended = activeUser();
        suspended.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void login_validCredentials_issuesTokens() {
        User user = activeUser();
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(tokenProvider.generateAccessToken(eq("user-pid"), any())).thenReturn("access-token");
        when(tokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);
        when(userMapper.toResponse(user)).thenReturn(USER_RESPONSE);

        AuthResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user()).isSameAs(USER_RESPONSE);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ---- refresh ----

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_revokedToken_detectsReuseAndRevokesAllSessions() {
        User user = activeUser();
        RefreshToken revoked = refreshToken(user, Instant.now().plus(1, ChronoUnit.DAYS), Instant.now());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("reuse");

        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(1L), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        User user = activeUser();
        RefreshToken expired = refreshToken(user, Instant.now().minus(1, ChronoUnit.DAYS), null);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refresh_inactiveUser_throwsForbidden() {
        User suspended = activeUser();
        suspended.setStatus(UserStatus.SUSPENDED);
        RefreshToken active = refreshToken(suspended, Instant.now().plus(1, ChronoUnit.DAYS), null);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void refresh_validToken_rotatesAndIssuesNewPair() {
        User user = activeUser();
        RefreshToken active = refreshToken(user, Instant.now().plus(1, ChronoUnit.DAYS), null);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));
        when(tokenProvider.generateAccessToken(eq("user-pid"), any())).thenReturn("access-token");
        when(tokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);
        when(userMapper.toResponse(user)).thenReturn(USER_RESPONSE);

        AuthResponse response = authService.refresh("raw-refresh");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        // Rotation: the presented token is revoked, a fresh one persisted.
        assertThat(active.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ---- logout ----

    @Test
    void logout_activeToken_revokesIt() {
        User user = activeUser();
        RefreshToken active = refreshToken(user, Instant.now().plus(1, ChronoUnit.DAYS), null);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));

        authService.logout("raw-refresh");

        assertThat(active.getRevokedAt()).isNotNull();
    }

    @Test
    void logout_alreadyRevokedToken_keepsOriginalRevokedAt() {
        Instant originalRevokedAt = Instant.now().minus(1, ChronoUnit.HOURS);
        User user = activeUser();
        RefreshToken revoked = refreshToken(user, Instant.now().plus(1, ChronoUnit.DAYS), originalRevokedAt);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        authService.logout("raw-refresh");

        assertThat(revoked.getRevokedAt()).isEqualTo(originalRevokedAt);
    }

    @Test
    void logout_unknownToken_isNoOp() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("raw-refresh");
        // No exception, nothing persisted.
    }

    // ---- verifyEmail ----

    private static final String OTP = "123456";

    /** Bản sao thuật toán hash của AuthService để dựng token có hash khớp OTP. */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private EmailVerificationToken otpToken(User user, String otp, Instant expiresAt) {
        return EmailVerificationToken.builder()
                .user(user)
                .otpHash(sha256(otp))
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void verifyEmail_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(EMAIL, OTP))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verifyEmail_alreadyVerified_throwsConflict() {
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> authService.verifyEmail(EMAIL, OTP))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void verifyEmail_noActiveOtp_throwsUnauthorized() {
        User user = activeUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByIdDesc(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(EMAIL, OTP))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyEmail_expiredOtp_throwsUnauthorized() {
        User user = activeUser();
        user.setEmailVerified(false);
        EmailVerificationToken expired = otpToken(user, OTP, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByIdDesc(1L))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verifyEmail(EMAIL, OTP))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_wrongOtp_incrementsAttemptsAndKeepsTokenUsable() {
        User user = activeUser();
        user.setEmailVerified(false);
        EmailVerificationToken token = otpToken(user, OTP, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByIdDesc(1L))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(EMAIL, "000000"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Incorrect");

        assertThat(token.getAttempts()).isEqualTo(1);
        assertThat(token.getUsedAt()).isNull();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_tooManyWrongAttempts_locksOtp() {
        User user = activeUser();
        user.setEmailVerified(false);
        EmailVerificationToken token = otpToken(user, OTP, Instant.now().plus(10, ChronoUnit.MINUTES));
        token.setAttempts(4); // lần sai này là lần thứ 5 → khóa
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByIdDesc(1L))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(EMAIL, "000000"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Too many");

        assertThat(token.getUsedAt()).isNotNull();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_correctOtp_marksVerifiedAndIssuesTokens() {
        User user = activeUser();
        user.setEmailVerified(false);
        EmailVerificationToken token = otpToken(user, OTP, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findTopByUserIdAndUsedAtIsNullOrderByIdDesc(1L))
                .thenReturn(Optional.of(token));
        when(tokenProvider.generateAccessToken(eq("user-pid"), any())).thenReturn("access-token");
        when(tokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);
        when(userMapper.toResponse(user)).thenReturn(USER_RESPONSE);

        AuthResponse response = authService.verifyEmail(EMAIL, OTP);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        // Auto sign-in: nhập đúng OTP là có luôn cặp token.
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ---- resendVerification ----

    @Test
    void resendVerification_unknownEmail_isSilentNoOp() {
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.empty());

        authService.resendVerification(EMAIL);

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationOtp(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerification_alreadyVerified_doesNothing() {
        User verified = activeUser(); // emailVerified = true
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(verified));

        authService.resendVerification(EMAIL);

        verify(emailVerificationTokenRepository, never()).invalidateActiveByUserId(any(), any());
        verify(emailService, never()).sendVerificationOtp(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerification_unverifiedUser_invalidatesOldTokensAndResends() {
        User unverified = activeUser();
        unverified.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(unverified));

        authService.resendVerification(EMAIL);

        verify(emailVerificationTokenRepository).invalidateActiveByUserId(eq(1L), any(Instant.class));
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationOtp(eq(EMAIL), eq("Full Name"), anyString());
    }
}
