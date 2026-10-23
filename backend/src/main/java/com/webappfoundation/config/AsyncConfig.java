package com.webappfoundation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Async} so transactional notification listeners (e.g. email on a support reply) run off
 * the request thread, and {@code @Scheduled} for periodic jobs (e.g. the inventory reservation-TTL sweep).
 * Uses Spring's defaults — sufficient for the low-volume work the foundation does. Generated projects can
 * define their own {@code TaskExecutor} bean to tune the pool without touching this class.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
