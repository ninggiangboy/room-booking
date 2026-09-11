package dev.ngb.backend.config;

import java.sql.SQLException;

import dev.ngb.backend.model.JsonDocument;
import org.postgresql.util.PGobject;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;

/** Registers PostgreSQL-specific value conversions needed by Spring Data JDBC entities. */
@Configuration
public class JdbcConversionConfig {

    /**
     * Allows PostgreSQL extension values such as {@code CITEXT} to populate Java strings, and maps
     * {@link JsonDocument} onto {@code jsonb} in both directions.
     *
     * @return JDBC conversions used by repository entity mapping
     */
    @Bean
    JdbcCustomConversions jdbcCustomConversions() {
        return JdbcCustomConversions.create(
                JdbcPostgresDialect.INSTANCE,
                configurer -> {
                    configurer.registerConverter(PgObjectToStringConverter.INSTANCE);
                    configurer.registerConverter(PgObjectToJsonDocumentConverter.INSTANCE);
                    configurer.registerConverter(JsonDocumentToPgObjectConverter.INSTANCE);
                });
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

    /** Reads a {@code jsonb} column into the typed wrapper the entities declare. */
    @ReadingConverter
    private enum PgObjectToJsonDocumentConverter
            implements Converter<PGobject, @Nullable JsonDocument> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Wraps the driver's JSON text, preserving {@code null} values.
         *
         * @param source PostgreSQL {@code jsonb} value
         * @return wrapped JSON document, or {@code null} when the column is null
         */
        @Override
        public @Nullable JsonDocument convert(PGobject source) {
            String value = source.getValue();
            return value == null ? null : new JsonDocument(value);
        }
    }

    /**
     * Writes a {@link JsonDocument} as a typed {@code jsonb} parameter.
     *
     * <p>The explicit {@code PGobject} type is what makes the bind work: the driver sends an untyped
     * string as {@code text}, and PostgreSQL will not implicitly cast {@code text} to {@code jsonb}.
     * </p>
     */
    @WritingConverter
    private enum JsonDocumentToPgObjectConverter implements Converter<JsonDocument, PGobject> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Binds the JSON text under the {@code jsonb} type name.
         *
         * @param source JSON document to persist
         * @return driver value typed as {@code jsonb}
         * @throws IllegalStateException when the driver rejects the value, which cannot happen for
         *         text the {@link JsonDocument} constructor has already accepted
         */
        @Override
        public PGobject convert(JsonDocument source) {
            PGobject target = new PGobject();
            target.setType("jsonb");
            try {
                target.setValue(source.value());
            } catch (SQLException e) {
                throw new IllegalStateException("Driver rejected a jsonb parameter", e);
            }
            return target;
        }
    }
}
