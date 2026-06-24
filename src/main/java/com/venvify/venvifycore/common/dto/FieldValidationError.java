package com.venvify.venvifycore.common.dto;

/**
 * Chi tiết lỗi validate của một field, trả về trong {@code data} của ApiResponse khi 400.
 */
public record FieldValidationError(String field, String message) {
}
