package com.venvify.venvifycore.user.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.exception.ResourceNotFoundException;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.mapper.UserMapper;
import com.venvify.venvifycore.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /** Hồ sơ của user đang đăng nhập. Principal = public_id (do JwtAuthenticationFilter set). */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal String publicId) {
        UserResponse user = userRepository.findByPublicId(publicId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }
}
