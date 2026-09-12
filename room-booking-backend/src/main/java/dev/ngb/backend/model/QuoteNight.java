package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One night of a quote.
 *
 * <p>Exists so a guest asking why Friday costs more gets an answer, and so a later date-level change
 * has per-night amounts to work from — refunding two nights of a five-night stay is guesswork without
 * them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("quote_nights")
public class QuoteNight {

    /** Primary key of the night. */
    @Id
    private @Nullable UUID id;
    /** Quote this night belongs to. */
    private UUID quoteId;
    /** The night, in the property's local calendar. */
    private LocalDate stayDate;
    /** ISO 4217 currency of both amounts. */
    private String currency;
    /** Rate before adjustments, in minor units. */
    private long baseAmountMinor;
    /** Net of the adjustments applied to this night, in minor units. */
    private long adjustmentTotalMinor;
    /** What this night contributes to the quote, in minor units; base plus adjustments. */
    private long netAmountMinor;
    /** Materialized price this night was taken from, when it came from one. */
    private @Nullable UUID dailyPriceComponentId;
    /** Version of that price, recorded so the night can be explained after the fact. */
    private @Nullable Long priceVersion;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
