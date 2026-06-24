package com.venvify.venvifycore.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Mở rộng {@link BaseEntity} cho các entity hỗ trợ xóa mềm (SPEC/CLAUDE.md §4 Soft Delete).
 * Repository phải lọc {@code isDeleted = false}; endpoint DELETE set cờ thay vì xóa cứng.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
