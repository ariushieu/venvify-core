package com.venvify.venvifycore.common.exception;

/** Lỗi xác thực (401): sai thông tin đăng nhập, refresh token không hợp lệ/hết hạn. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
