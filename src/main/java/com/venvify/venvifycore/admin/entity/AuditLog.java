package com.venvify.venvifycore.admin.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Vết mọi mutation của admin (P6 §4) — append-only 3 lớp như ledger_entries:
 * (1) {@code @Immutable} Hibernate không bao giờ UPDATE, (2) không API sửa/xóa,
 * (3) trigger V7 chặn UPDATE/DELETE từ tầng DB. Ghi CÙNG transaction với mutation.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_admin", columnList = "admin_id"),
        @Index(name = "idx_audit_target", columnList = "target_type, target_public_id")
})
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /** Mã hành động, vd USER_BAN / EVENT_TAKEDOWN / REVIEW_HIDE. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_public_id", length = 36)
    private String targetPublicId;

    /** Ngữ cảnh tự do (reason takedown…) — TEXT, không cấu trúc cứng. */
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;
}
