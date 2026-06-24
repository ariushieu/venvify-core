package com.venvify.venvifycore.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật JPA Auditing để @CreatedDate / @LastModifiedDate trong {@code BaseEntity} hoạt động.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
