package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One complete tax determination, with the inputs that produced it.
 *
 * <p>The request facts are snapshotted beside the result because a tax figure that cannot be
 * recomputed from what was known at the time is not evidence. Tax is the one number that gets audited
 * years later by someone who was not in the room, and by then the listing, the rates, and the party's
 * profile have all moved on.</p>
 *
 * <p>A failed determination is recorded rather than discarded: an attempt that produced no figure is
 * itself the explanation for why a quote was refused.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("tax_calculations")
public class TaxCalculation {

    /** Primary key of the calculation. */
    @Id
    private @Nullable UUID id;
    /** Quote this was computed for. */
    private @Nullable UUID quoteId;
    /** Booking it was computed for, when it was not a quote. */
    private @Nullable UUID bookingId;
    /** Market whose rules governed it. */
    private String marketCode;
    /** ISO 4217 currency of both amounts. */
    private String currency;
    /** Everything the calculation read, as an immutable JSON snapshot. */
    private JsonDocument requestFactSnapshot;
    /** Version of the tax content applied, so the same answer can be produced again. */
    private String contentVersion;
    /** Where the result came from. */
    private TaxCalculationSource calculationSource;
    /** Provider account that produced it; required for a provider result. */
    private @Nullable UUID providerAccountId;
    /** Provider's own reference for the response; required for a provider result. */
    private @Nullable String providerReference;
    /** Amount tax was computed on, in minor units. */
    private long taxableBaseMinor;
    /** Total tax determined, in minor units. */
    private long taxTotalMinor;
    /** Whether this determination still stands. */
    private TaxCalculationStatus status;
    /** UTC instant the determination was made. */
    private Instant calculatedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
