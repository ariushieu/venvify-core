package com.venvify.venvifycore.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho các job nền (EscrowReleaseJob 15', ReconciliationJob 03:17). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
