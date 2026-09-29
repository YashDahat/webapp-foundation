package com.webappfoundation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} so transactional notification listeners (e.g. email on a support reply) run
 * off the request thread. Uses Spring's default SimpleAsyncTaskExecutor — sufficient for the low-volume
 * transactional email the foundation sends. Generated projects can define their own {@code TaskExecutor}
 * bean to tune the pool without touching this class.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
