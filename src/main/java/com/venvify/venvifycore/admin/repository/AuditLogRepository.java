package com.venvify.venvifycore.admin.repository;

import com.venvify.venvifycore.admin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only: chỉ save; không có API sửa/xóa (trigger V7 là chốt DB). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
