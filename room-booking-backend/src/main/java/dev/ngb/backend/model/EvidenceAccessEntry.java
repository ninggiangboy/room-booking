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
 * Who read which protected artifact, for what declared purpose, under what authority.
 *
 * <p>Being assigned a case is not permission to browse private messages, exact addresses or identity
 * documents; access is re-evaluated at read time and this row is the proof it was.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("evidence_access_log")
public class EvidenceAccessEntry {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case evidence item this row belongs to. */
    private UUID caseEvidenceItemId;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Which access kind this row carries. */
    private EvidenceAccessKind accessKind;
    /** Which actor type this row carries. */
    private EvidenceAccessActorType actorType;
    /** The actor account holder this row belongs to. */
    private @Nullable UUID actorAccountHolderId;
    /** Actor role code. */
    private @Nullable String actorRoleCode;
    /** Reference to the external recipient, held in its owning system rather than copied here. */
    private @Nullable String externalRecipientReference;
    /** The purpose the read was made for, which is what authorizes it. */
    private String declaredPurpose;
    /** The authority policy version this row belongs to. */
    private @Nullable UUID authorityPolicyVersionId;
    /** The agent skill grant this row belongs to. */
    private @Nullable UUID agentSkillGrantId;
    /** Reference to the approval, held in its owning system rather than copied here. */
    private @Nullable String approvalReference;
    /** Whether emergency access was used. */
    private boolean breakGlass;
    /** Reference to the break glass, held in its owning system rather than copied here. */
    private @Nullable String breakGlassReference;
    /** Whether a review is owed afterwards, which break-glass always is. */
    private boolean postUseReviewRequired;
    /** Which fields were actually read, where field-level recording is warranted. */
    private String[] fieldsAccessed;
    /** Outcome. */
    private EvidenceAccessOutcome outcome;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String denialReason;
    /** UTC instant accessed. */
    private Instant accessedAt;
    /** UTC instant access expires. */
    private @Nullable Instant accessExpiresAt;
    /** Reference to the session, held in its owning system rather than copied here. */
    private @Nullable String sessionReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
