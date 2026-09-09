package dev.ngb.backend.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the single application clock.
 *
 * <p>{@code @Configuration} marks this as a source of Spring beans rather than a business service.
 * Every time-dependent decision must read this clock so a fixed clock can replace it in tests and
 * so one command uses one decision instant.</p>
 *
 * <p>Reference: {@code docs/features/date-time-and-time-zone-handling.md}.</p>
 */
@Configuration
public class TimeConfig {

    /**
     * Resolution of PostgreSQL {@code timestamptz}, which stores microseconds rather than nanoseconds.
     */
    public static final Duration DATABASE_TIME_RESOLUTION = Duration.ofNanos(1_000L);

    /**
     * Returns the production clock used by token, account, and calendar services.
     *
     * <p>{@code @Bean} makes the returned {@link Clock} injectable by type. The clock ticks at the
     * resolution PostgreSQL can actually store, so an {@link Instant} held in memory stays equal to
     * the same value read back from a {@code timestamptz} column instead of being silently
     * truncated on write.</p>
     *
     * @return UTC clock truncated to database resolution
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), DATABASE_TIME_RESOLUTION);
    }

}
