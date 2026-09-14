package dev.ngb.backend.support.internal.model.claim;

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

import dev.ngb.backend.support.internal.model.CaseAppealProgress;


/**
 * A claim against a specific accepted booking, by a specific claimant, about a specific interval.
 *
 * <p>Its state is its own: a claim can be decided while payment is pending, recovery is unfinished and an
 * appeal is open. Late or incomplete claims are recorded with the reason they were late.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("damage_claims")
public class DamageClaim {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Human-quotable claim reference, unique across the platform. */
    private String claimReference;
    /** The claimant account holder this row belongs to. */
    private UUID claimantAccountHolderId;
    /** The respondent account holder this row belongs to. */
    private @Nullable UUID respondentAccountHolderId;
    /** The booking this row belongs to. */
    private UUID bookingId;
    /** The booking revision this row belongs to. */
    private @Nullable UUID bookingRevisionId;
    /** The operational stay this row belongs to. */
    private @Nullable UUID operationalStayId;
    /** The incident this row belongs to. */
    private @Nullable UUID incidentId;
    /** The listing this row belongs to. */
    private @Nullable UUID listingId;
    /** UTC instant the alleged loss is said to have begun. */
    private Instant occurrenceFrom;
    /** UTC instant it is said to have ended, when the claimant can bound it. */
    private @Nullable Instant occurrenceUntil;
    /** UTC instant the claimant says they discovered it. */
    private Instant discoveredAt;
    /** UTC instant submitted. */
    private Instant submittedAt;
    /** UTC instant claim window deadline. */
    private @Nullable Instant claimWindowDeadlineAt;
    /** UTC instant respondent deadline. */
    private @Nullable Instant respondentDeadlineAt;
    /** ISO 4217 alphabetic code the requested are denominated in. */
    private String requestedCurrency;
    /** Requested total, in integer minor units of its currency. */
    private long requestedTotalMinor;
    /** Accepted total, in integer minor units of its currency. */
    private @Nullable Long acceptedTotalMinor;
    /** The deductible applied, in minor units. */
    private long deductibleMinor;
    /** What has already been recovered elsewhere, in minor units. */
    private long priorRecoveryMinor;
    /** Where the eligibility stands. */
    private ClaimEligibilityState eligibilityState;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String ineligibilityReason;
    /** Whether the claim arrived after its window closed. */
    private boolean lateSubmission;
    /** Why it was late, which a late claim always records. */
    private @Nullable String lateSubmissionReason;
    /** The exception review that let a late or excluded claim proceed. */
    private @Nullable String exceptionReviewReference;
    /** The protection program version this row belongs to. */
    private @Nullable UUID protectionProgramVersionId;
    /** The coverage snapshot this row belongs to. */
    private @Nullable UUID coverageSnapshotId;
    /** Where the state stands. */
    private DamageClaimState state;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Responsibility outcome. */
    private ResponsibilityOutcome responsibilityOutcome;
    /** The deterministic valuation rules the items were priced under. */
    private @Nullable String valuationPolicyReference;
    /** UTC instant decided. */
    private @Nullable Instant decidedAt;
    /** The case decision this row belongs to. */
    private @Nullable UUID caseDecisionId;
    /** UTC instant settled. */
    private @Nullable Instant settledAt;
    /** UTC instant closed. */
    private @Nullable Instant closedAt;
    /** The damage deposit this claim may draw on, which payment owns. */
    private @Nullable String depositReference;
    /** The finance hold taken while the claim is decided, if one was. */
    private @Nullable UUID payoutHoldId;
    /** Where the appeal stands. */
    private CaseAppealProgress appealState;
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
