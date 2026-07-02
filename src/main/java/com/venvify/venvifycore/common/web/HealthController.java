package com.venvify.venvifycore.common.web;

import com.venvify.venvifycore.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check cho Docker healthcheck / CD pipeline / load balancer (public, không cần token).
 * UP = app sống VÀ chạm được DB (SELECT 1) — container chỉ được coi healthy khi phục vụ được request thật.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP")));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(),
                            "Database unreachable", Map.of("status", "DOWN")));
        }
    }
}
