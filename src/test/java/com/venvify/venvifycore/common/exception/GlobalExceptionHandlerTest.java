package com.venvify.venvifycore.common.exception;

import com.venvify.venvifycore.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleLockConflict_returnsConflict() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleLockConflict(new OptimisticLockingFailureException("stale"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("concurrently");
    }

    @Test
    void handleDataIntegrity_returnsConflict() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("duplicate"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("conflicts");
    }
}
