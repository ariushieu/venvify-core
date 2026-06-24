package com.venvify.venvifycore.common.entity;

import com.venvify.venvifycore.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Lớp cơ sở cho MỌI entity (SPEC §5.5, plan §0.3).
 * - {@code id}: khóa nội bộ BIGINT, KHÔNG expose ra ngoài.
 * - {@code publicId}: UUID công khai dùng cho API/FE.
 * - {@code createdAt}/{@code updatedAt}: audit tự động (cần bật @EnableJpaAuditing).
 * - {@code version}: optimistic locking, chống ghi đè đồng thời.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", updatable = false, nullable = false, unique = true, length = 36)
    private String publicId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void ensurePublicId() {
        if (publicId == null) {
            publicId = UuidV7.generateString();
        }
    }
}
