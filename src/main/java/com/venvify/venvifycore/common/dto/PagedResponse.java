package com.venvify.venvifycore.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Bao kết quả phân trang gọn gàng cho FE (CLAUDE.md §4 — pagination bắt buộc).
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
