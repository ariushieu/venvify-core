package com.venvify.venvifycore.user.service;

import com.venvify.venvifycore.common.exception.BadRequestException;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.UserStatus;
import com.venvify.venvifycore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lookup user cho module khác (ma trận master §2 — module chéo gọi qua SERVICE,
 * không import UserRepository). Auth/đăng ký vẫn ở {@link AuthService}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
}
