package com.venvify.venvifycore.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 modular hóa autoconfig nên KHÔNG expose sẵn bean {@link ObjectMapper} để inject.
 * Khai báo tường minh: tự nạp mọi module có trên classpath (JavaTime cho Instant, ParameterNames
 * cho record DTO, Jdk8) + ngày dạng ISO-8601. Dùng chung cho MVC converter và serialize JSON ở
 * filter/handler security.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
