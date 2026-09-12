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
 * What the platform concluded about one host capability, from what evidence, under which policy.
 *
 * <p>This is the row that separates evidence from authority. A provider returning "identity matched"
 * grants nothing; a policy reads that case, the screening adjudications, and the registrations, and
 * records its conclusion here — naming the {@code market_policy_bundles} version it applied, so the
 * decision stays explainable after the rules change.</p>
 *
 * <p>Authority itself still lives in one place. A granted decision writes a row in
 * {@code capability_grants} and points at it through {@link #grantedGrantId}; no service asks this
 * table whether a host may publish, it asks the grant. The database requires that link, which is what
 * ties platform authority back to the evidence that justified it.</p>
 *
 * <p>There is no {@code @Version}: decisions are superseded, never edited, so the reasoning behind a
 * past decision survives the next one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_eligibility_decisions")
public class HostEligibilityDecision {

    /** Primary key of the decision. */
    @Id
    private @Nullable UUID id;
    /** Legal profile the decision is about. */
    private UUID hostLegalProfileId;
    /** Market whose rules were applied. */
    private String marketCode;
    /** Which capability was decided. */
    private HostCapability capability;
    /** What was concluded. */
    private EligibilityDecisionType decision;
    /** Stable reason for that conclusion. */
    private String reasonCode;
    /** Policy bundle version the decision applied. */
    private @Nullable UUID policyBundleId;
    /** Verification cases the decision rested on. */
    private UUID @Nullable [] evidenceCaseIds;
    /** Operator who decided, where a person did. */
    private @Nullable UUID decidedBy;
    /** Whether policy decided without a person. */
    private boolean decidedAutomatically;
    /** Capability grant this decision produced; required for a grant, absent for a refusal. */
    private @Nullable UUID grantedGrantId;
    /** UTC instant the decision was made. */
    private Instant decidedAt;
    /** UTC instant the decision stops applying and must be revisited. */
    private @Nullable Instant effectiveUntil;
    /** Decision that replaced this one. */
    private @Nullable UUID supersededBy;
    /** UTC instant the row was written. */
    private Instant createdAt;
}
