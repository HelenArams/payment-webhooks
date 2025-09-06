package com.helenaramyan.webhooks.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable {@link Clock} so that anything time-dependent (scheduling, signatures,
 * timestamps) can be driven by a fixed or mutable clock in tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
