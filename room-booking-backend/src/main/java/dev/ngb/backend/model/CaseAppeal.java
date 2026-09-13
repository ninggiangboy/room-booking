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
 * One appeal against one eligible decision.
 *
 * <p>Independence is a recorded property of the reviewer rather than an intention. A successful appeal
 * produces a superseding decision and new idempotent commands; it never erases the original.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_appeals")
public class CaseAppeal {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The challenged decision this row belongs to. */
    private UUID challengedDecisionId;
    /** Position of this row within its parent, unique there. */
    private short appealNumber;
    /** The appellant account holder this row belongs to. */
    private UUID appellantAccountHolderId;
    /** Which appellant role this row carries. */
    private AppellantRole appellantRole;
    /** The representative participant this row belongs to. */
    private @Nullable UUID representativeParticipantId;
    /** The approved ground the appeal is brought on. */
    private String groundsCode;
    /** Reference to the grounds, held in its owning system rather than copied here. */
    private @Nullable String groundsReference;
    /** Requested outcome. */
    private AppealRequestedOutcome requestedOutcome;
    /** Evidence submitted with the appeal that the original decision did not see. */
    private UUID[] newEvidenceItemIds;
    /** BCP 47 locale the content is written in. */
    private String locale;
    /** UTC instant submitted. */
    private Instant submittedAt;
    /** Reference to the deadline policy, held in its owning system rather than copied here. */
    private String deadlinePolicyReference;
    /** UTC instant submission deadline. */
    private @Nullable Instant submissionDeadlineAt;
    /** Whether late submission. */
    private boolean lateSubmission;
    /** Where the state stands. */
    private CaseAppealState state;
    /** Where the eligibility stands. */
    private AppealEligibilityState eligibilityState;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String ineligibilityReason;
    /** Whether policy requires a reviewer independent of the original decider. */
    private boolean independenceRequired;
    /** The reviewer account holder this row belongs to. */
    private @Nullable UUID reviewerAccountHolderId;
    /** Who took the challenged decision, so independence can be checked. */
    private @Nullable UUID originalDeciderAccountHolderId;
    /** UTC instant assigned. */
    private @Nullable Instant assignedAt;
    /** Outcome. */
    private @Nullable CaseAppealOutcome outcome;
    /** The superseding decision an appeal produced; affirming needs none. */
    private @Nullable UUID resultingDecisionId;
    /** UTC instant decided. */
    private @Nullable Instant decidedAt;
    /** UTC instant communicated. */
    private @Nullable Instant communicatedAt;
    /** UTC instant closed. */
    private @Nullable Instant closedAt;
    /** Where a complaint goes when the appeal does not settle it. */
    private @Nullable String externalComplaintRoute;
    /** Reference to the external complaint, held in its owning system rather than copied here. */
    private @Nullable String externalComplaintReference;
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
