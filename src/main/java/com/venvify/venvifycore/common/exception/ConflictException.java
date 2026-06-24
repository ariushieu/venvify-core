package com.venvify.venvifycore.common.exception;

/**
 * Ném khi xung đột trạng thái/ràng buộc duy nhất (HTTP 409),
 * ví dụ email đã tồn tại, đã claim slot, đã review.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
