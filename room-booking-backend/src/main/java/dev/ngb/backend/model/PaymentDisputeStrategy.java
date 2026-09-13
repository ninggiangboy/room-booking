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
 * What this domain decides about a provider dispute that payment owns.
 *
 * <p>Payment normalizes the provider's own observations; duplicating them here would give the company
 * two accounts of one provider's answer. What lives here is whether to accept or represent, which
 * evidence goes, who authorized the submission, and against which deadline.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_dispute_strategies")
public class PaymentDisputeStrategy {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The payment dispute this row belongs to. */
    private UUID paymentDisputeId;
    /** Strategy. */
    private DisputeStrategy strategy;
    /** Strategy reason code. */
    private String strategyReasonCode;
    /** The decided by account holder this row belongs to. */
    private @Nullable UUID decidedByAccountHolderId;
    /** UTC instant decided. */
    private @Nullable Instant decidedAt;
    /** Reference to the approval, held in its owning system rather than copied here. */
    private @Nullable String approvalReference;
    /** The case decision this row belongs to. */
    private @Nullable UUID caseDecisionId;
    /** The manifest this row belongs to. */
    private @Nullable UUID manifestId;
    /** Digest of the evidence manifest that went with the representment. */
    private @Nullable String manifestDigest;
    /** UTC instant provider deadline. */
    private Instant providerDeadlineAt;
    /** The authorized command that submits to the provider. */
    private @Nullable String submissionCommandId;
    /** UTC instant submitted. */
    private @Nullable Instant submittedAt;
    /** Submission outcome. */
    private DisputeSubmissionOutcome submissionOutcome;
    /** Reference to the narrative, held in its owning system rather than copied here. */
    private @Nullable String narrativeReference;
    /** Whether a model drafted the narrative, which then needs a human signature. */
    private boolean narrativeDraftedByModel;
    /** The person who signed it before it reached the provider. */
    private @Nullable UUID narrativeReviewedByAccountHolderId;
    /** Where the state stands. */
    private DisputeStrategyState state;
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
