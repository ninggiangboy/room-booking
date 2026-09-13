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
 * What one feature means, versioned, with its privacy approval attached.
 *
 * <p>Frozen once approved, because a decision replayed a year later must apply the definition that
 * was in force then. Two rules live here as check constraints rather than as review practice: a
 * feature derived from a protected attribute needs a named permitted purpose and a legal review
 * reference, and a feature kept for fairness auditing may not also be an operational decision input.
 * A feature served on the critical path must additionally say how stale it may be and what it means
 * when absent.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_feature_definitions")
public class RiskFeatureDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key; unique together with the version. */
    private String featureKey;
    /** Version of this definition. */
    private int featureVersion;
    /** The shape of the computed value. */
    private FeatureValueType valueType;
    /** What the feature is computed about. */
    private FeatureEntityScope entityScope;
    /** Length of the observation window, where the feature has one. */
    private @Nullable Integer windowSeconds;
    /** Which clock the window is measured against. */
    private FeatureEventTimeRule eventTimeRule;
    /** What an absent value means; required for online serving. */
    private @Nullable FeatureMissingSemantics missingValueSemantics;
    /** Where the transformation itself is defined. */
    private String transformationReference;
    /** Signal types this feature reads. */
    private String[] sourceSignalTypes;
    /** How money inputs are treated across currencies. */
    private @Nullable String currencyHandling;
    /** How civil time inputs are treated. */
    private @Nullable String timeZoneHandling;
    /** Team accountable for the definition. */
    private String ownerTeam;
    /** Whether the feature is read on the critical path. */
    private boolean onlineServing;
    /** How stale an online value may be; required for online serving. */
    private @Nullable Integer maxAgeSeconds;
    /** Evidence that offline and online computation agree. */
    private @Nullable String parityTestReference;
    /** Whether the feature derives from a protected attribute or a close proxy. */
    private boolean protectedAttributeDerived;
    /** The purpose legal review established; required for a protected-attribute feature. */
    private @Nullable String permittedPurpose;
    /** The legal review itself; required for a protected-attribute feature. */
    private @Nullable String legalReviewReference;
    /** Whether the feature exists only to audit disparate impact. */
    private boolean fairnessAuditOnly;
    /**
     * Whether the feature may be an input to an operational decision.
     *
     * <p>Cannot be true at the same time as {@code fairnessAuditOnly}: the fairness population is
     * access-separated from production decision inputs, and that separation is a constraint here
     * rather than a promise.</p>
     */
    private boolean operationalUsePermitted;
    /** Whether the definition may be used. */
    private FeatureDefinitionStatus status;
    /** When it was approved; present for anything past draft. */
    private @Nullable Instant approvedAt;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
