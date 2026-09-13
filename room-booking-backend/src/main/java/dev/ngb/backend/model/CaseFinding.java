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
import org.springframework.data.relational.core.mapping.Table;

/**
 * An authorized interpretation, and the only kind of row a decision may rest on.
 *
 * <p>Names the question it answers, the proof standard it was held to, the evidence it cites and the
 * evidence that contradicts it. {@code INCONCLUSIVE} is a real answer and is never rounded towards
 * whichever party filed first.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_findings")
public class CaseFinding {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Finding question. */
    private String findingQuestion;
    /** Which subject kind this row carries. */
    private FindingSubjectKind subjectKind;
    /** The subject account holder this row belongs to. */
    private @Nullable UUID subjectAccountHolderId;
    /** Reference to the subject, held in its owning system rather than copied here. */
    private @Nullable String subjectReference;
    /** Outcome. */
    private FindingOutcome outcome;
    /** Proof standard. */
    private ProofStandard proofStandard;
    /** Confidence between zero and one, where the policy permits one at all. */
    private @Nullable BigDecimal confidence;
    /** The evidence the finding rests on; a conclusion either way must cite some. */
    private UUID[] citedEvidenceIds;
    /** The exact fragments within that evidence. */
    private String[] citedFragments;
    /** Evidence that points the other way, kept rather than omitted. */
    private UUID[] contradictingEvidenceIds;
    /** What could not be established, which an inconclusive finding must name. */
    private String[] unresolvedGaps;
    /** Other readings of the same evidence. */
    private String[] alternativeExplanations;
    /** The investigation template version this row belongs to. */
    private UUID investigationTemplateVersionId;
    /** Reference to the finding policy, held in its owning system rather than copied here. */
    private String findingPolicyReference;
    /** Which author actor type this row carries. */
    private FindingAuthorType authorActorType;
    /** The author account holder this row belongs to. */
    private @Nullable UUID authorAccountHolderId;
    /** The reviewed by account holder this row belongs to. */
    private @Nullable UUID reviewedByAccountHolderId;
    /** UTC instant reviewed. */
    private @Nullable Instant reviewedAt;
    /** How far this finding may be reused before it has to be established again. */
    private FindingReusableScope reusableScope;
    /** UTC instant concluded. */
    private Instant concludedAt;
    /** The finding that replaced this one, written in the same transaction. */
    private @Nullable UUID supersededByFindingId;
    /** UTC instant superseded. */
    private @Nullable Instant supersededAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String supersessionReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
