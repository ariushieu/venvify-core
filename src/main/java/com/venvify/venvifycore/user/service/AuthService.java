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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xác thực & quản lý phiên (CLAUDE.md §3-§5). Access JWT stateless + refresh stateful (rotation,
 * reuse-detection). Đăng ký tạo luôn ví USER (D12) và gửi email xác thực; login bị chặn tới khi verify.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;
    private final UserMapper userMapper;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Value("${app.mail.verification-token-expiration}")
    private long verificationTokenExpirationMs;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public UserResponse register(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
        userRepository.save(user);

        // Mỗi user có một ví USER (double-entry D12).
        walletRepository.save(Wallet.builder()
                .user(user)
                .accountType(WalletAccountType.USER)
                .currency("VND")
                .balanceCached(0L)
                .build());

        sendVerification(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedFalse(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Email not verified");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is not active");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        Instant now = Instant.now();

        if (stored.getRevokedAt() != null) {
            // Token đã thu hồi mà vẫn bị dùng → nghi bị lộ, thu hồi toàn bộ phiên của user.
            refreshTokenRepository.revokeAllActiveByUserId(stored.getUser().getId(), now);
            throw new UnauthorizedException("Refresh token reuse detected");
        }
        if (!stored.isActive(now)) {
            throw new UnauthorizedException("Refresh token expired");
        }
        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is not active");
        }

        // Rotation: thu hồi token cũ, phát cặp mới.
        stored.setRevokedAt(now);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
            }
        });
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken evt = emailVerificationTokenRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid verification token"));
        if (!evt.isUsable(Instant.now())) {
            throw new UnauthorizedException("Verification token expired or already used");
        }
        evt.setUsedAt(Instant.now());
        evt.getUser().setEmailVerified(true);
    }

    @Transactional
    public void resendVerification(String email) {
        // Controller luôn trả message như nhau để không tiết lộ email có tồn tại hay không.
        userRepository.findByEmailAndDeletedFalse(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                emailVerificationTokenRepository.invalidateActiveByUserId(user.getId(), Instant.now());
                sendVerification(user);
            }
        });
    }

    // ---- helpers ----

    private void sendVerification(User user) {
        String rawToken = generateRawToken();
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plusMillis(verificationTokenExpirationMs))
                .build());
        String link = baseUrl + "/auth/verify-email?token=" + rawToken;
        // Tiện dev khi email chưa tới (token trong DB là hash, không tra ngược được). Tắt ở prod.
        log.debug("Email verification link for {}: {}", user.getEmail(), link);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), link);
    }

    private AuthResponse issueTokens(User user) {
        Set<String> roleNames = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
        String accessToken = tokenProvider.generateAccessToken(user.getPublicId(), roleNames);

        String rawRefresh = generateRawToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefresh))
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .build());

        long expiresInSeconds = Duration.ofMillis(tokenProvider.getAccessTokenExpirationMs()).toSeconds();
        return new AuthResponse(accessToken, rawRefresh, "Bearer", expiresInSeconds, userMapper.toResponse(user));
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
