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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One immutable set of cancellation terms.
 *
 * <p>Everything needed to reproduce an old settlement is here: the typed rule document, the schema it
 * conforms to, the evaluator build that reads it, the cutoff semantics, and a content hash that makes
 * an altered document detectable rather than plausible.</p>
 *
 * <p>Publication freezes the row by trigger. Retirement is the one move left afterwards, because a
 * version has to be able to stop applying without its past applications changing.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("cancellation_policy_versions")
public class CancellationPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Family this version belongs to. */
    private UUID policyDefinitionId;
    /** Human-readable label, unique within the family. */
    private String versionLabel;
    /** Monotonic number within the family. */
    private int versionNumber;
    /** The typed rules, interpreted by the evaluator named below. */
    private JsonDocument ruleDocument;
    /** Schema the rule document conforms to. */
    private String ruleSchemaVersion;
    /** Evaluator build that reads it. Settlements cite this. */
    private String evaluatorVersion;
    /** SHA-256 of the rule document, lowercase hex. */
    private String contentHash;
    /** Suite of worked examples this version was verified against. */
    private @Nullable String testVectorSuiteVersion;
    /**
     * Which clock a cutoff is measured against. A rule saying "48 hours before check-in" settles
     * different amounts in different zones, so the basis travels with the terms.
     */
    private PolicyCutoffBasis cutoffBasis;
    /** How that clock is resolved at evaluation time. */
    private PolicyCutoffZoneSource cutoffTimeZoneSource;
    /** Catalogue of reason codes this version may produce. */
    private String reasonCatalogVersion;
    /** Start of the half-open window in which this version may be quoted. */
    private Instant effectiveFrom;
    /** End of that window, exclusive. Open-ended while null. */
    private @Nullable Instant effectiveTo;
    /** Publication state. Frozen once published. */
    private CancellationPolicyState state;
    /** Who signed the terms off. */
    private @Nullable UUID approvedByActorId;
    /** When they did. */
    private @Nullable Instant approvedAt;
    /** When the version became quotable. */
    private @Nullable Instant publishedAt;
    /** When it stopped being offered. */
    private @Nullable Instant retiredAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this version may be quoted to a guest.
     *
     * @return {@code true} when it is published
     */
    public boolean isQuotable() {
        return state == CancellationPolicyState.PUBLISHED;
    }

    /**
     * Whether this version is the applicable one at an instant.
     *
     * <p>The window is half-open: a version that ended and one that began at the same instant do not
     * both apply.</p>
     *
     * @param at instant to test
     * @return {@code true} when the instant falls inside the effective window
     */
    public boolean isEffectiveAt(Instant at) {
        return !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
    }
}
