package dev.ngb.backend.config;

import org.postgresql.util.PGobject;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;

/** Registers PostgreSQL-specific value conversions needed by Spring Data JDBC entities. */
@Configuration
public class JdbcConversionConfig {

    /**
     * Allows PostgreSQL extension values such as {@code CITEXT} to populate Java strings.
     *
     * @return JDBC conversions used by repository entity mapping
     */
    @Bean
    JdbcCustomConversions jdbcCustomConversions() {
        return JdbcCustomConversions.create(
                JdbcPostgresDialect.INSTANCE,
                configurer -> configurer.registerConverter(PgObjectToStringConverter.INSTANCE));
    }

    /** Converts an extension-backed PostgreSQL scalar to its textual representation. */
    @ReadingConverter
    private enum PgObjectToStringConverter implements Converter<PGobject, @Nullable String> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Returns the driver's scalar text, preserving {@code null} values.
         *
         * @param source PostgreSQL extension value
         * @return underlying text value
         */
        @Override
        public @Nullable String convert(PGobject source) {
            return source.getValue();
        }
    }
}
