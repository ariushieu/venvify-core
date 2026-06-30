package com.venvify.venvifycore.user.dto;

/**
 * Trả khi đăng nhập / refresh thành công.
 * {@code expiresIn} = số GIÂY còn hiệu lực của access token.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
