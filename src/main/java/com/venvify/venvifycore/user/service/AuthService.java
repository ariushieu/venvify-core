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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * reuse-detection). Đăng ký tạo luôn ví USER (D12) và gửi OTP xác thực email; login bị chặn tới khi
 * verify. Nhập đúng OTP thì phát luôn cặp token (auto sign-in).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 32;
    /** OTP 6 số chỉ có 10^6 khả năng → khóa mã sau chừng này lần nhập sai để chặn brute force. */
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final LoginAttemptService loginAttemptService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Value("${app.mail.verification-token-expiration}")
    private long verificationTokenExpirationMs;

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
        // T6: account đang khóa vì sai quá nhiều → trả 401 y hệt sai mật khẩu, không lộ trạng thái khóa.
        if (loginAttemptService.isLocked(request.email())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        User user = userRepository.findByEmailAndDeletedFalse(request.email()).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Đếm cả email không tồn tại — không phân biệt được từ ngoài, chống dò email luôn thể.
            loginAttemptService.recordFailure(request.email());
            throw new UnauthorizedException("Invalid email or password");
        }
        loginAttemptService.clearFailures(request.email());
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

    /**
     * Đối chiếu OTP với mã đang hiệu lực mới nhất của user. Đúng → đánh dấu verified và
     * phát luôn cặp token (khỏi bắt đăng nhập lại); sai quá {@link #MAX_OTP_ATTEMPTS} lần → khóa mã.
     */
    @Transactional
    public AuthResponse verifyEmail(String email, String otp) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid OTP"));
        if (user.isEmailVerified()) {
            throw new ConflictException("Email already verified");
        }

        Instant now = Instant.now();
        EmailVerificationToken evt = emailVerificationTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByIdDesc(user.getId())
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new UnauthorizedException("OTP expired — request a new code"));

        if (!evt.getOtpHash().equals(sha256(otp))) {
            evt.setAttempts(evt.getAttempts() + 1);
            if (evt.getAttempts() >= MAX_OTP_ATTEMPTS) {
                evt.setUsedAt(now);
                throw new UnauthorizedException("Too many incorrect attempts — request a new code");
            }
            throw new UnauthorizedException("Incorrect OTP");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is not active");
        }

        evt.setUsedAt(now);
        user.setEmailVerified(true);
        return issueTokens(user);
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
        String otp = generateOtp();
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .otpHash(sha256(otp))
                .expiresAt(Instant.now().plusMillis(verificationTokenExpirationMs))
                .build());
        sendVerificationAfterCommit(user.getEmail(), user.getFullName(), otp);
    }

    private void sendVerificationAfterCommit(String email, String fullName, String otp) {
        Runnable send = () -> emailService.sendVerificationOtp(email, fullName, otp);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    /** OTP 6 chữ số, zero-pad (000000–999999). */
    private static String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
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
