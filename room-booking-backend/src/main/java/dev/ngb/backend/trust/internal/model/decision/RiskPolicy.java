package dev.ngb.backend.trust.internal.model.decision;

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
import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.market.internal.model.market.Market;

import dev.ngb.backend.market.internal.model.market.Market;
import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.trust.internal.model.RiskActionTier;


/**
 * One immutable, effective-dated version of the rules a decision is taken under.
 *
 * <p>Frozen once active, because a decision names the policy version and epoch it bound, and editing
 * the version in place would rewrite the reason for every decision already taken under it. Two live
 * versions of the same key may not overlap in time, so the applicable rule is never a race.
 * Activation is guarded: a standing rejection blocks it, and a tier-three or tier-four policy needs
 * two approvers who are not its author.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_policies")
public class RiskPolicy {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key; unique together with the version. */
    private String policyKey;
    /** Version of this policy. */
    private int policyVersion;
    /** The configuration set an evaluation binds and records. */
    private String policyEpoch;
    /** How widely the version applies. */
    private RiskPolicyScopeType scopeType;
    /** What it applies to; absent exactly for a global scope. */
    private @Nullable UUID scopeId;
    /** Market the version is written for. */
    private @Nullable UUID marketId;
    /** Protected actions this version decides. */
    private String[] actionKeys;
    /** The highest tier it can act on, which sets the approval it needs. */
    private RiskActionTier maxActionTier;
    /** Order among versions that could both apply. */
    private int priority;
    /** The typed input schema the rule document expects. */
    private String inputSchemaReference;
    /** The rule tree itself. */
    private JsonDocument ruleDocument;
    /** How rule results map onto outcomes. */
    private JsonDocument outcomeMapping;
    /** How internal reasons map onto approved user reason families. */
    private JsonDocument userReasonMapping;
    /** Queue a manual review from this policy goes to. */
    private @Nullable String reviewRouteQueueKey;
    /** Appeal path for adverse outcomes under this version. */
    private @Nullable String appealRoute;
    /** Team accountable for the version. */
    private String ownerTeam;
    /** Who wrote it; may not also approve it. */
    private UUID authoredByAccountHolderId;
    /** The replay it was judged on; required before it can act outside shadow. */
    private @Nullable String simulationEvidenceReference;
    /** Share of eligible evaluations this version decides. */
    private int rolloutPercentage;
    /** Whether it decides nothing and is only measured. */
    private boolean shadowMode;
    /** Whether its contribution is currently switched off. */
    private boolean killSwitchEngaged;
    /** Why it was switched off. */
    private @Nullable String killSwitchReason;
    /** Whether this was an emergency change, which must expire and be reviewed afterwards. */
    private boolean emergency;
    /** When the emergency change is to be reviewed. */
    private @Nullable Instant postUseReviewAt;
    /** Where the version stands in its release. */
    private RiskPolicyStatus status;
    /** When it began to apply. */
    private @Nullable Instant effectiveFrom;
    /** When it stopped; may be brought forward, never extended. */
    private @Nullable Instant effectiveUntil;
    /** When it was activated. */
    private @Nullable Instant activatedAt;
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
