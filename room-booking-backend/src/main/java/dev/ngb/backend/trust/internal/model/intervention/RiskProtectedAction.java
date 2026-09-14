package dev.ngb.backend.trust.internal.model.intervention;

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

import dev.ngb.backend.trust.internal.model.RiskActionTier;
import dev.ngb.backend.trust.internal.model.RiskDecisionOutcome;
import dev.ngb.backend.trust.internal.model.RiskEnforcementDomain;


/**
 * One operation that may be evaluated before an authoritative domain commits it.
 *
 * <p>The registry exists because a generic risk endpoint must not accept an action name invented by a
 * client. Every action declares its latency budget, the outcomes it will accept, what happens when
 * evaluation fails, and whether it can be appealed -- in advance, rather than in whichever service
 * happens to call it. A decision naming an unregistered or draft action, or carrying an outcome this
 * row did not permit, is refused by trigger.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_protected_actions")
public class RiskProtectedAction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key services evaluate against; unique. */
    private String actionKey;
    /** How much this action can cost if it goes wrong. */
    private RiskActionTier actionTier;
    /** The domain that owns the command and enforces the decision. */
    private RiskEnforcementDomain enforcementOwnerDomain;
    /** Milliseconds the evaluation may take before the failure mode applies. */
    private int maxDecisionLatencyMs;
    /**
     * The outcomes this action will accept, drawn from {@link RiskDecisionOutcome}.
     *
     * <p>A check constraint holds the set inside the six, and a trigger refuses any decision carrying
     * an outcome absent from it.</p>
     */
    private String[] permittedOutcomes;
    /** How stale a feature may be and still be used on this path. */
    private @Nullable Integer requiredFeatureMaxAgeS;
    /** What happens when no decision can be produced in time. */
    private RiskFailureMode failureMode;
    /** The registered fallback; present exactly when the failure mode names one. */
    private @Nullable RiskDecisionOutcome fallbackOutcome;
    /** Longest a hold on this action may run; required if holds are permitted. */
    private @Nullable Integer holdLimitSeconds;
    /** Whether a person must always be involved; forced true for tier four. */
    private boolean humanEscalationRequired;
    /** How much the subject is told about a refusal. */
    private RiskDisclosurePolicy disclosurePolicy;
    /** Whether a refusal can be appealed. */
    private boolean appealAvailable;
    /** The documented hazard behind withholding an appeal path; required when there is none. */
    private @Nullable String appealSuppressionReason;
    /** Whether this action may currently be evaluated. */
    private RiskActionStatus status;
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
