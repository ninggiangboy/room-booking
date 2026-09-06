package dev.ngb.backend.config;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

/** Configures Spring Data JDBC to maintain entity creation and modification timestamps. */
@Configuration
@EnableJdbcAuditing(dateTimeProviderRef = "jdbcAuditingDateTimeProvider")
public class JdbcAuditingConfig {

    /**
     * Uses the application's shared clock so audited timestamps remain deterministic in tests.
     *
     * @param clock application's source of UTC time
     * @return provider used by Spring Data JDBC auditing callbacks
     */
    @Bean
    DateTimeProvider jdbcAuditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
