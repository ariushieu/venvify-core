package com.venvify.venvifycore.user.service;

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
}
