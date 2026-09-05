package dev.ngb.backend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a shared clock so time-dependent code can be replaced with a fixed clock in tests.
 *
 * <p>{@code @Configuration} marks this as a source of Spring beans rather than a business service.</p>
 */
@Configuration
public class TimeConfig {

    /**
     * Returns the production clock used by token and account services.
     *
     * <p>{@code @Bean} makes the returned {@link Clock} injectable by type.</p>
     *
     * @return system clock fixed to UTC
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
