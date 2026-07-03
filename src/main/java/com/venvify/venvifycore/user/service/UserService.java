package com.venvify.venvifycore.user.service;

import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.mapper.UserMapper;
import com.venvify.venvifycore.user.repository.RefreshTokenRepository;
import com.venvify.venvifycore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Lookup user cho module khác (ma trận master §2 — module chéo gọi qua SERVICE,
 * không import UserRepository) + nghiệp vụ tài khoản cho admin (P6 §4).
 * Auth/đăng ký vẫn ở {@link AuthService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;

    /**
     * Host public theo vanity handle (plan P3 §2.4). Host bị ban (SUSPENDED) hoặc đã xóa
     * → 404 như không tồn tại — không quảng bá storefront của tài khoản khóa.
     */
    @Transactional(readOnly = true)
    public User getActiveHostByHandle(String handle) {
        return userRepository.findByHostHandleAndDeletedFalse(handle)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Host not found"));
    }

    @Transactional(readOnly = true)
    public User getByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Resolve user ACTIVE theo ĐÚNG MỘT trong hai định danh — email hoặc host handle
     * (R4 transfer: người nhận phải là user đã đăng ký). Message 404 cố ý chung chung:
     * không dùng làm kênh dò email tồn tại kèm trạng thái tài khoản.
     */
    @Transactional(readOnly = true)
    public User resolveActiveByEmailOrHandle(String email, String handle) {
        boolean byEmail = email != null && !email.isBlank();
        boolean byHandle = handle != null && !handle.isBlank();
        if (byEmail == byHandle) {
            throw new BadRequestException("Provide exactly one of email or handle");
        }
        var found = byEmail
                ? userRepository.findByEmailAndDeletedFalse(email.trim().toLowerCase())
                : userRepository.findByHostHandleAndDeletedFalse(handle.trim());
        return found.filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));
    }

    // ---- admin (P6 §4 — AdminModerationService gọi, audit ghi phía admin cùng tx) ----

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> adminSearch(String q, UserStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid page or size");
        }
        String normalized = (q == null || q.isBlank()) ? null : q.trim();
        return PagedResponse.of(userRepository
                .adminSearch(normalized, status, PageRequest.of(page, size))
                .map(userMapper::toResponse));
    }

    /**
     * Ban = SUSPENDED + thu hồi TOÀN BỘ refresh token (master §5): không refresh được nữa,
     * access token cũ sống tối đa 15' (stateless — chấp nhận, STOMP kick chờ P4).
     * Login sau đó trả 401 generic (memory account-lifecycle). Idempotent.
     */
    @Transactional
    public User ban(String targetPublicId) {
        User target = getByPublicId(targetPublicId);
        if (target.getRoles().contains(Role.ADMIN)) {
            throw new BadRequestException("Administrators cannot be banned");
        }
        if (target.getStatus() == UserStatus.SUSPENDED) {
            return target;
        }
        target.setStatus(UserStatus.SUSPENDED);
        refreshTokenRepository.revokeAllActiveByUserId(target.getId(), Instant.now());
        log.info("User {} suspended, all refresh tokens revoked", target.getId());
        return userRepository.save(target);
    }

    @Transactional
    public User unban(String targetPublicId) {
        User target = getByPublicId(targetPublicId);
        if (target.getStatus() != UserStatus.SUSPENDED) {
            throw new BadRequestException("User is not suspended");
        }
        target.setStatus(UserStatus.ACTIVE);
        return userRepository.save(target);
    }

    // ---- KPI (P6 §4 dashboard) ----

    @Transactional(readOnly = true)
    public long countUsers() {
        return userRepository.countByDeletedFalse();
    }

    @Transactional(readOnly = true)
    public long countHosts() {
        return userRepository.countByRole(Role.HOST);
    }
}
