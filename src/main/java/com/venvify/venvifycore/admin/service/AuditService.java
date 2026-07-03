package com.venvify.venvifycore.admin.service;

import com.venvify.venvifycore.admin.entity.AuditLog;
import com.venvify.venvifycore.admin.repository.AuditLogRepository;
import com.venvify.venvifycore.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RULE P6 §4: audit ghi TRONG CÙNG transaction với mutation — mutation fail thì không có
 * audit row và ngược lại. {@code MANDATORY} enforce điều đó: gọi ngoài transaction là bug.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User admin, String action, String targetType, String targetPublicId, String detail) {
        auditLogRepository.save(AuditLog.builder()
                .admin(admin)
                .action(action)
                .targetType(targetType)
                .targetPublicId(targetPublicId)
                .detail(detail)
                .build());
    }
}
