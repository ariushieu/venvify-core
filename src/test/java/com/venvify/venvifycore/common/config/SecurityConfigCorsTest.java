package com.venvify.venvifycore.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void corsConfigurationSource_allowsConfiguredOriginAndMethods() {
        SecurityConfig config = new SecurityConfig(null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins",
                new String[]{"http://localhost:5173", "https://app.venvify.com"});

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfigurations().get("/**");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://app.venvify.com");
        assertThat(cors.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isFalse();
    }
}
