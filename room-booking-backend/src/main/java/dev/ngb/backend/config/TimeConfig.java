package dev.ngb.backend.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the single application clock and enforces the Coordinated Universal Time (UTC) runtime.
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

    /**
     * Fails startup when the Java Virtual Machine (JVM) default zone is not UTC.
     *
     * <p>{@code @PostConstruct} runs this check once the bean is created, which covers production
     * startup and every Spring test context. The default zone cannot be treated as cosmetic:
     * Spring Data JDBC converts {@code LocalDate}, {@code LocalTime}, and {@code LocalDateTime}
     * through {@link java.sql.Timestamp} using {@link ZoneId#systemDefault()}, and the PostgreSQL
     * driver advertises the same zone as the database session time zone. A non-UTC default
     * therefore changes stored values and the result of server-side date expressions, and it
     * exposes daylight-saving gaps that UTC does not have.</p>
     *
     * @throws IllegalStateException when the default zone is not equivalent to UTC
     */
    @PostConstruct
    void requireUtcDefaultZone() {
        ZoneId defaultZone = ZoneId.systemDefault();
        if (!isUtcEquivalent(defaultZone)) {
            throw new IllegalStateException(
                    "JVM default time zone must be UTC but was '" + defaultZone
                            + "'. Start the process with TZ=UTC or -Duser.timezone=UTC; see "
                            + "docs/features/date-time-and-time-zone-handling.md");
        }
    }

    /**
     * Reports whether a zone behaves exactly like UTC at every instant.
     *
     * <p>Identifier comparison is deliberately avoided so functional aliases such as {@code UTC},
     * {@code Etc/UTC}, {@code GMT}, and {@code Z} are all accepted while any zone with an offset or
     * a daylight-saving transition is rejected.</p>
     *
     * @param zone zone to inspect
     * @return {@code true} when the zone has a fixed zero offset
     */
    private static boolean isUtcEquivalent(ZoneId zone) {
        return zone.getRules().isFixedOffset()
                && ZoneOffset.UTC.equals(zone.getRules().getOffset(Instant.EPOCH));
    }
}
