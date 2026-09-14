package dev.ngb.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Starts the Room Booking backend and anchors component scanning at {@code dev.ngb.backend}.
 *
 * <p>{@code @SpringBootApplication} combines configuration registration, sensible Spring Boot
 * auto-configuration, and component scanning of this package and all descendants. Every direct
 * sub-package is a Spring Modulith module; see {@code docs/architecture/modular-monolith.md}.</p>
 *
 * <p>{@code @EnableAsync} is required for {@code @ApplicationModuleListener} (the default mechanism
 * for cross-module, post-commit events): the annotation composes {@code @Async}, and without
 * asynchronous method execution enabled it silently runs synchronously instead, which would defeat
 * the isolation it exists to provide. See
 * {@code docs/architecture/event-publication-registry.md}.</p>
 */
@SpringBootApplication
@EnableAsync
public class RoomBookingBackendApplication {

    /**
     * Launches the application with the configuration selected from command-line arguments.
     *
     * <p>The Java Virtual Machine (JVM) default time zone is pinned to Coordinated Universal Time
     * (UTC) before Spring starts. This is load-bearing rather than cosmetic: Spring Data JDBC
     * converts {@code LocalDate}, {@code LocalTime}, and {@code LocalDateTime} through
     * {@link java.sql.Timestamp} using {@link java.time.ZoneId#systemDefault()}, and the PostgreSQL
     * driver reports the same default zone as the database session time zone. Leaving it to the
     * host would make persistence and every {@code CURRENT_DATE}-style expression depend on where
     * the process happens to run; see {@code docs/features/date-time-and-time-zone-handling.md}.</p>
     *
     * @param args Spring Boot command-line arguments
     */
    static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RoomBookingBackendApplication.class, args);
    }

}
