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
 * The immutable record of what was decided, under which version of which rules, on which inputs.
 *
 * <p>A correction, appeal, late provider result or recovery creates a new decision naming this one. The
 * input and evidence digests are what make a decision reproducible years later.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_decisions")
public class CaseDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Canonical command identity; a retry replays this decision rather than making a second one. */
    private String commandId;
    /** Which decision kind this row carries. */
    private CaseDecisionKind decisionKind;
    /** The support policy version this row belongs to. */
    private UUID supportPolicyVersionId;
    /** The authority policy version this row belongs to. */
    private UUID authorityPolicyVersionId;
    /** Which remedy catalogue the decision drew from. */
    private @Nullable String remedyCatalogKey;
    /** Which version of that catalogue. */
    private @Nullable Integer remedyCatalogVersion;
    /** The protection program version this row belongs to. */
    private @Nullable UUID protectionProgramVersionId;
    /** Decision basis. */
    private DecisionBasis decisionBasis;
    /** Precedence rung. */
    private PrecedenceRung precedenceRung;
    /** Outcome. */
    private CaseDecisionOutcome outcome;
    /** Countable reason codes; a refusal always carries at least one. */
    private String[] resultReasonCodes;
    /** What the decision could not settle, kept rather than rounded away. */
    private String[] unresolvedUncertainty;
    /** Digest of the canonical inputs, so the decision can be re-derived later. */
    private String inputDigest;
    /** Digest of the exact evidence set the decision saw. */
    private String evidenceManifestDigest;
    /** Digest of the decision itself; an approval binds this value, not merely the row. */
    private String decisionDigest;
    /** Which decided by actor type this row carries. */
    private DecisionActorType decidedByActorType;
    /** The decided by account holder this row belongs to. */
    private @Nullable UUID decidedByAccountHolderId;
    /** The authority evaluation captured at the moment of deciding. */
    private String authoritySnapshotReference;
    /** Whether the conflict rules actually ran; an effective decision requires it. */
    private boolean conflictChecksPassed;
    /** Whether emergency authority was used, which always earns a review. */
    private boolean breakGlass;
    /** Reference to the break glass, held in its owning system rather than copied here. */
    private @Nullable String breakGlassReference;
    /** The approved exception code, required on the exceptional-review rung. */
    private @Nullable String manualExceptionCode;
    /** Internal reasoning, never shown to a participant as written. */
    private @Nullable String internalReasonReference;
    /** The approved template a participant is shown instead. */
    private @Nullable String participantExplanationTemplateKey;
    /** Whether appeal available. */
    private boolean appealAvailable;
    /** UTC instant appeal deadline. */
    private @Nullable Instant appealDeadlineAt;
    /** Appeal route. */
    private @Nullable String appealRoute;
    /** Disclosure constraint. */
    private @Nullable DecisionDisclosureConstraint disclosureConstraint;
    /** Where the state stands. */
    private CaseDecisionState state;
    /** Effective from. */
    private @Nullable Instant effectiveFrom;
    /** UTC instant the decision stops applying; it may be brought forward, never extended. */
    private @Nullable Instant expiresAt;
    /** UTC instant communicated. */
    private @Nullable Instant communicatedAt;
    /** The decision that replaced this one, written in the same transaction. */
    private @Nullable UUID supersededByDecisionId;
    /** UTC instant superseded. */
    private @Nullable Instant supersededAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String supersessionReason;
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
