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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An authority's tax rule as the platform understood it, with the source it was read from.
 *
 * <p>A tax rate is not ours to infer. A calculation that cannot name the rule it followed, the
 * document that rule was read from, and the person who approved that reading cannot be defended to
 * the authority that levied it — so a published version requires an approval record, and nobody
 * publishes a tax rate alone.</p>
 *
 * <p>Only one published version of a given tax may be in force per authority at any instant; the
 * database refuses an overlap, because two calculations a second apart would otherwise charge
 * different tax on the same night.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("tax_rule_versions")
public class TaxRuleVersion {

    /** Primary key of the version. */
    @Id
    private @Nullable UUID id;
    /** Jurisdiction, as a country code with optional subdivisions. */
    private String jurisdictionCode;
    /** Authority that levies the tax, as it names itself. */
    private String authorityName;
    /** Which tax this governs. */
    private TaxType taxType;
    /** Monotonic number within the jurisdiction, authority, and tax type. */
    private int versionNumber;
    /** When the rule applies, as an immutable JSON condition. */
    private JsonDocument applicabilityPayload;
    /** How the amount is computed, as an immutable JSON formula. */
    private JsonDocument formulaPayload;
    /** Rate charged, as a percentage; mutually exclusive with a flat amount. */
    private @Nullable BigDecimal ratePercent;
    /** Flat amount charged in minor units, such as a per-night visitor levy. */
    private @Nullable Long flatAmountMinor;
    /** ISO 4217 currency of that amount; present exactly when it is. */
    private @Nullable String flatAmountCurrency;
    /** Who is obliged to hand the tax to the authority. */
    private TaxRemittanceModel remittanceModel;
    /** How the figure is rounded to whole minor units. */
    private TaxRoundingMode roundingMode;
    /** At which point in the calculation that rounding happens. */
    private TaxRoundingBoundary roundingBoundary;
    /** The law, circular, or notice this reading came from. */
    private String sourceReference;
    /** Approval that accepted the reading; required once published. */
    private @Nullable UUID approvalRecordId;
    /** Lowercase hex SHA-256 of the rule content. */
    private String contentDigest;
    /** Whether the rule may be cited, and whether it may still be edited. */
    private PublicationState publicationState;
    /** UTC instant it was published. */
    private @Nullable Instant publishedAt;
    /** UTC instant the rule began applying. */
    private Instant effectiveFrom;
    /** UTC instant it stopped; absent while open-ended. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Reports whether this rule was in force at an instant.
     *
     * @param instant the command's decision instant
     * @return {@code true} when the version is published and the instant falls in its period
     */
    public boolean isInForceAt(Instant instant) {
        return publicationState == PublicationState.PUBLISHED
                && !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }
}
