package com.venvify.venvifycore.common.exception;

/**
 * Ném khi request không hợp lệ về mặt nghiệp vụ (HTTP 400).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
