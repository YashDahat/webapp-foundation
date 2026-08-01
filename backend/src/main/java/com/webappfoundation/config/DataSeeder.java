package com.webappfoundation.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Foundation placeholder — business-specific seed data goes here.
 * AdminInitializer already creates the admin user; add domain entities below.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            // Add business-specific seed data here
        };
    }
}
