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

import dev.ngb.backend.trust.internal.model.RiskDecisionOutcome;

/**
 * One evaluation, with one immutable outcome.
 *
 * <p>The canonical identity -- action, actor, resource, command and policy epoch -- is unique, so a
 * retry replays the recorded decision rather than producing a second one. What was decided is frozen
 * by trigger; what moves is the operational projection and the pointer to whatever superseded it. A
 * hold needs a deadline, a limit needs a scope the domain can read, an adverse outcome needs an
 * approved reason family, and allowing while a mandatory fact was missing must name the registered
 * fallback it used.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_decisions")
public class RiskDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Key used to correlate the snapshot and predictions behind this decision. */
    private String evaluationKey;
    /** The registered action evaluated; must be active and must permit the outcome. */
    private String protectedAction;
    /** Who proposed the action. */
    private UUID actorSubjectId;
    /** What kind of resource was acted on. */
    private @Nullable String resourceType;
    /** Which resource. */
    private @Nullable UUID resourceId;
    /** The resource version the decision was taken against. */
    private @Nullable Long resourceVersion;
    /** The domain command this decision belongs to. */
    private @Nullable UUID commandId;
    /** The caller's key; one key is one answer. */
    private @Nullable String clientIdempotencyKey;
    /** The configuration set bound at the start of the evaluation. */
    private String policyEpoch;
    /** The policy version applied; must belong to that epoch and cover the action. */
    private @Nullable UUID riskPolicyId;
    /** Market the evaluation was made in. */
    private @Nullable UUID marketId;
    /** Session the action was proposed from. */
    private @Nullable UUID authSessionId;
    /** The answer; frozen once written. */
    private RiskDecisionOutcome outcome;
    /** Which eligibility dimension a hold stops. */
    private @Nullable String holdDimension;
    /** The exact limit a domain must enforce. */
    private @Nullable JsonDocument limitScope;
    /** Machine-readable reasons; never shown to the subject. */
    private String[] internalReasons;
    /** The approved reason family the subject may be told. */
    private @Nullable String userReasonFamily;
    /** The snapshot this decision was taken on. */
    private @Nullable UUID riskFeatureSnapshotId;
    /** Predictions consulted, if any. */
    private UUID[] modelPredictionIds;
    /** Which registered fallback produced the answer, where one did. */
    private @Nullable RiskFallbackMode fallbackMode;
    /** Whether a required fact was unavailable at evaluation time. */
    private boolean mandatoryFactsMissing;
    /** Whether this is containment before confirmation; must then expire. */
    private boolean precautionary;
    /** Who decided. */
    private RiskDecider decidedBy;
    /** The person, where a person decided. */
    private @Nullable UUID decidedByAccountHolderId;
    /** Correlation identifier across the request. */
    private @Nullable UUID correlationId;
    /** When the decision was taken. */
    private Instant evaluatedAt;
    /** When it stops being enforceable. */
    private @Nullable Instant expiresAt;
    /** Whether it still applies; the outcome itself never changes. */
    private RiskDecisionProjection projection;
    /** The decision this one replaces. */
    private @Nullable UUID supersedesDecisionId;
    /** The decision that replaced this one. */
    private @Nullable UUID supersededByDecisionId;
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
