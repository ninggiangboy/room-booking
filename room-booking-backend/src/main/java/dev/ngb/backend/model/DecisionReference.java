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
import org.springframework.data.relational.core.mapping.Table;

/**
 * A monitoring index of one domain decision that a model or a fallback fed into.
 *
 * <p>The decision itself belongs to the domain that made it; this row carries that domain's own
 * identifier rather than a copy of its state, so nothing here can drift away from the authoritative
 * record or be mistaken for it. What it does carry is the pairing the evaluation needs: which
 * policy version applied, which prediction or fallback was available, and whether the policy
 * overrode the recommendation -- because a model is not credited for a decision the policy
 * changed.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("decision_references")
public class DecisionReference {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which domain owns the decision. */
    private DecisionDomain decisionDomain;
    /** That domain's own identifier for the decision; never a copy of its state. */
    private String decisionReference;
    /** What kind of decision it was. */
    private String decisionKind;
    /** Which version of the domain's policy governed it. */
    private String policyVersion;
    /** Digest of the authoritative facts the decision was taken on. */
    private @Nullable String factualSnapshotDigest;
    /** The prediction that was available, where one was. */
    private @Nullable UUID predictionRecordId;
    /** Why no usable prediction was available, or why one was not acted on. */
    private @Nullable String fallbackReason;
    /** The exposure the decision was taken under, where one applies. */
    private @Nullable UUID experimentExposureId;
    /** What the model or rule recommended. */
    private @Nullable String recommendedAction;
    /** What was actually done. */
    private String selectedAction;
    /** Whether policy changed the outcome away from the recommendation. */
    private boolean policyOverrideApplied;
    /** Which deterministic constraints applied, and how. */
    private @Nullable String constraintResults;
    /** Approved reason codes the domain recorded. */
    private @Nullable String reasonCodes;
    /** Who the decision was taken by or on behalf of. */
    private DecisionActorKind actorKind;
    /** Correlation identifier tying this to the request that produced it. */
    private @Nullable String correlationId;
    /** UTC instant the decision was taken. */
    private Instant decidedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
