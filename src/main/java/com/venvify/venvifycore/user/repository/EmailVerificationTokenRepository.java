package com.venvify.venvifycore.user.repository;

import com.venvify.venvifycore.user.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /** OTP đang hiệu lực mới nhất của user (mỗi lần gửi lại đều vô hiệu mã cũ trước). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationToken> findTopByUserIdAndUsedAtIsNullOrderByIdDesc(Long userId);

    /** Vô hiệu các OTP chưa dùng của user (khi gửi lại mã xác thực). */
    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    void invalidateActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
