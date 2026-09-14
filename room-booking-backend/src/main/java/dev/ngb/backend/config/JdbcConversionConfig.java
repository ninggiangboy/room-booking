package dev.ngb.backend.config;

import java.sql.SQLException;
import java.time.Duration;

import dev.ngb.backend.platform.BucketRange;
import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.platform.StayRange;
import org.postgresql.util.PGInterval;
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
     * {@link JsonDocument} onto {@code jsonb}, the two range value types onto their PostgreSQL
     * range types, and {@link Duration} onto {@code interval}, in both directions.
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
                    configurer.registerConverter(PgObjectToStayRangeConverter.INSTANCE);
                    configurer.registerConverter(StayRangeToPgObjectConverter.INSTANCE);
                    configurer.registerConverter(PgObjectToBucketRangeConverter.INSTANCE);
                    configurer.registerConverter(BucketRangeToPgObjectConverter.INSTANCE);
                    configurer.registerConverter(PgIntervalToDurationConverter.INSTANCE);
                    configurer.registerConverter(DurationToPgObjectConverter.INSTANCE);
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

    /** Reads a {@code daterange} column into the typed half-open range the entities declare. */
    @ReadingConverter
    private enum PgObjectToStayRangeConverter implements Converter<PGobject, @Nullable StayRange> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Parses the driver's range text, preserving {@code null} values.
         *
         * @param source PostgreSQL {@code daterange} value
         * @return the parsed stay range, or {@code null} when the column is null
         */
        @Override
        public @Nullable StayRange convert(PGobject source) {
            String value = source.getValue();
            return value == null ? null : StayRange.parse(value);
        }
    }

    /**
     * Writes a {@link StayRange} as a typed {@code daterange} parameter.
     *
     * <p>The explicit type name is what makes the bind work, and it is what lets the database apply
     * the GiST exclusion constraint that prevents double booking: an untyped string parameter would
     * arrive as {@code text}, which PostgreSQL will not implicitly cast to a range.</p>
     */
    @WritingConverter
    private enum StayRangeToPgObjectConverter implements Converter<StayRange, PGobject> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Binds the canonical half-open literal under the {@code daterange} type name.
         *
         * @param source stay range to persist
         * @return driver value typed as {@code daterange}
         * @throws IllegalStateException when the driver rejects the value, which cannot happen for a
         *         range the {@link StayRange} constructor has already accepted
         */
        @Override
        public PGobject convert(StayRange source) {
            PGobject target = new PGobject();
            target.setType("daterange");
            try {
                target.setValue(source.toRangeLiteral());
            } catch (SQLException e) {
                throw new IllegalStateException("Driver rejected a daterange parameter", e);
            }
            return target;
        }
    }

    /** Reads an {@code int4range} column into the value type the entities declare. */
    @ReadingConverter
    private enum PgObjectToBucketRangeConverter implements Converter<PGobject, @Nullable BucketRange> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Parses the driver's canonical range text, preserving {@code null} values.
         *
         * @param source PostgreSQL {@code int4range} value
         * @return the bucket range, or {@code null} when the column is null
         */
        @Override
        public @Nullable BucketRange convert(PGobject source) {
            String value = source.getValue();
            return value == null ? null : BucketRange.parse(value);
        }
    }

    /**
     * Writes a {@link BucketRange} as a typed {@code int4range} parameter.
     *
     * <p>The explicit type name is what makes the bind work, and it is what lets the database apply
     * the GiST exclusion constraint that keeps two variants of one epoch from claiming the same
     * bucket: an untyped string parameter would arrive as {@code text}, which PostgreSQL will not
     * implicitly cast to a range.</p>
     */
    @WritingConverter
    private enum BucketRangeToPgObjectConverter implements Converter<BucketRange, PGobject> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Binds the canonical half-open literal under the {@code int4range} type name.
         *
         * @param source bucket range to persist
         * @return driver value typed as {@code int4range}
         * @throws IllegalStateException when the driver rejects the value, which cannot happen for a
         *         range the {@link BucketRange} constructor has already accepted
         */
        @Override
        public PGobject convert(BucketRange source) {
            PGobject target = new PGobject();
            target.setType("int4range");
            try {
                target.setValue(source.toRangeLiteral());
            } catch (SQLException e) {
                throw new IllegalStateException("Driver rejected an int4range parameter", e);
            }
            return target;
        }
    }

    /**
     * Reads an {@code interval} column into a {@link Duration}.
     *
     * <p>A PostgreSQL interval can carry years and months, which are not fixed spans of time: how
     * long a month is depends on which month it falls in. The label horizons and maturity delays
     * this application stores are exact spans, so a year or month component means the value was
     * written by something other than this application and is rejected rather than silently
     * approximated -- a label horizon that quietly changed length by a day or three would move the
     * instant at which every outcome under it is allowed to mature.</p>
     */
    @ReadingConverter
    private enum PgIntervalToDurationConverter implements Converter<PGInterval, @Nullable Duration> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Converts the day and time components of the driver's interval into an exact duration.
         *
         * @param source PostgreSQL {@code interval} value
         * @return the equivalent duration
         * @throws IllegalArgumentException when the interval carries years or months, which have no
         *         exact length
         */
        @Override
        public @Nullable Duration convert(PGInterval source) {
            if (source.getYears() != 0 || source.getMonths() != 0) {
                throw new IllegalArgumentException(
                        "An interval carrying years or months has no exact length: " + source);
            }
            return Duration.ofDays(source.getDays())
                    .plusHours(source.getHours())
                    .plusMinutes(source.getMinutes())
                    .plusMillis(Math.round(source.getSeconds() * 1000.0));
        }
    }

    /**
     * Writes a {@link Duration} as a typed {@code interval} parameter.
     *
     * <p>The ISO-8601 text {@link Duration#toString()} produces is an interval literal PostgreSQL
     * accepts directly. The explicit type name is what makes the bind work, for the same reason it
     * does for the range and JSON converters: an untyped string arrives as {@code text}, and the
     * database will not implicitly cast it.</p>
     */
    @WritingConverter
    private enum DurationToPgObjectConverter implements Converter<Duration, PGobject> {
        /** Stateless converter singleton. */
        INSTANCE;

        /**
         * Binds the ISO-8601 duration text under the {@code interval} type name.
         *
         * @param source duration to persist
         * @return driver value typed as {@code interval}
         * @throws IllegalStateException when the driver rejects the value, which cannot happen for
         *         text {@link Duration#toString()} produced
         */
        @Override
        public PGobject convert(Duration source) {
            PGobject target = new PGobject();
            target.setType("interval");
            try {
                target.setValue(source.toString());
            } catch (SQLException e) {
                throw new IllegalStateException("Driver rejected an interval parameter", e);
            }
            return target;
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
