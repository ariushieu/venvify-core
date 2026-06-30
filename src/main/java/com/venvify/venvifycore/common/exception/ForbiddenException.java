package com.venvify.venvifycore.common.exception;

/** Bị từ chối (403): email chưa xác thực, tài khoản không ở trạng thái ACTIVE. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
