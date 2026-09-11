package dev.ngb.backend.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One immutable, effective-dated version of an approved rule set for a market.
 *
 * <p>Bundles are superseded, never edited. That is what makes a historical replay honest: a
 * cancellation decided in 2026 resolves the bundle version recorded on the booking, not the rules
 * that happen to be in force when someone asks about it in 2029.</p>
 *
 * <p>There is no {@code @Version} because the row never changes. The
 * {@code ex_market_policy_bundles_no_overlap} exclusion constraint guarantees at most one bundle of
 * each type is in force per market at any instant; without it, two decisions a second apart could
 * apply different approved rules.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_policy_bundles")
public class MarketPolicyBundle {

    /** Primary key of the bundle version. */
    @Id
    private @Nullable UUID id;
    /** Market the rules govern. */
    private UUID marketId;
    /** Entity accountable for operating under these rules. */
    private UUID legalEntityId;
    /** Which rule set this bundle carries. */
    private PolicyBundleType bundleType;
    /** Monotonic version number within this market and bundle type. */
    private int bundleVersion;
    /** SHA-256 digest of the approved document, proving what was approved. */
    private String contentDigest;
    /** Stable reference to the approved document in document storage. */
    private String documentReference;
    /** UTC instant from which these rules govern decisions, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant from which they no longer do, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
    /** Approval that authorized this version. */
    private @Nullable UUID approvalRecordId;
    /** Bundle version that replaced this one. */
    private @Nullable UUID supersededBy;
    /** UTC instant the row was written. */
    private Instant createdAt;

    /**
     * Reports whether this version governs decisions made at the supplied instant.
     *
     * <p>The span is half-open: a decision taken exactly at {@code effectiveUntil} is governed by
     * the succeeding version, matching how the database exclusion constraint ranges are defined.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when this version is in force at that instant
     */
    public boolean governsAt(Instant instant) {
        return !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }
}
