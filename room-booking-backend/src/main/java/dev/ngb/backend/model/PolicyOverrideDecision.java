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
 * Whether one booking qualifies for an override programme.
 *
 * <p>A declared event never changes a booking by itself. Each booking gets its own immutable
 * eligibility decision naming the programme version it was judged under and the evidence that was
 * weighed.</p>
 *
 * <p>A rejection is recorded as fully as an approval, because the guest will ask why, and because a
 * pattern of rejections is how a badly scoped programme is discovered. One judgement per booking per
 * programme: a second would let a guest re-apply until somebody said yes.</p>
 *
 * <p>Decisions are written once, so the row carries no optimistic lock and no update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("policy_override_decisions")
public class PolicyOverrideDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Booking being judged. */
    private UUID bookingId;
    /** Revision it was judged against. */
    private UUID bookingRevisionId;
    /** Programme applied to. */
    private UUID programId;
    /** The exact scope and funding it was judged under. */
    private UUID programVersionId;
    /** Kind of actor that applied. */
    private BookingActorType requestedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID requestedByActorId;
    /** Who judged it. */
    private OverrideReviewerType reviewerType;
    /** Which person. Required for anything but an automated decision. */
    private @Nullable UUID reviewerActorId;
    /** Evidence weighed, held outside this row. */
    private @Nullable String evidenceReference;
    /** How strong that evidence was. */
    private OverrideEvidenceRequirement evidenceClass;
    /** Structured reason for the outcome. */
    private String reasonCode;
    /** The judgement. */
    private OverrideDecisionResult result;
    /** Message key for what the applicant is told. */
    private @Nullable String resultNoteKey;
    /** ISO 4217 code, where an amount was granted. */
    private @Nullable String currency;
    /** Amount granted. Only ever present on an approval. */
    private @Nullable Long grantedAmountMinor;
    /** When the override starts applying. */
    private Instant effectiveFrom;
    /** When it stops. */
    private @Nullable Instant expiresAt;
    /** Approval record, where the decision needed one. */
    private @Nullable String approvalReference;
    /** Correlation identifier for the work that wrote it. */
    private String correlationId;
    /** When the judgement was made. */
    private Instant decidedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether the override applies to the booking at an instant.
     *
     * @param at instant to test
     * @return {@code true} when the decision approved it and has not expired
     */
    public boolean isEffectiveAt(Instant at) {
        return result == OverrideDecisionResult.APPROVED
                && !at.isBefore(effectiveFrom)
                && (expiresAt == null || at.isBefore(expiresAt));
    }
}
