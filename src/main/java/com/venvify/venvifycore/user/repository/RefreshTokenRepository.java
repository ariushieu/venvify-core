package com.venvify.venvifycore.user.repository;

import com.venvify.venvifycore.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Thu hồi toàn bộ token còn hiệu lực của user (dùng khi phát hiện reuse). */
    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.user.id = :userId and rt.revokedAt is null")
    void revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
