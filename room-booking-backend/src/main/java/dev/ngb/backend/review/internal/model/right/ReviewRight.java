package dev.ngb.backend.review.internal.model.right;

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
import dev.ngb.backend.platform.RetentionClass;

import dev.ngb.backend.review.internal.model.ReviewDirection;

/**
 * One bounded permission to review, in one direction, with its policy frozen onto the row.
 *
 * <p>A completed booking creates a right, not a review. The right is not reopened because an author
 * withdrew or moderation removed the text; revocation requires a superseding booking fact.</p>
 *
 * <p>When a co-host writes for the host, both the acting and the represented party are recorded. It
 * must never read as though the principal personally wrote what somebody else wrote.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_rights")
public class ReviewRight {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Cycle this right belongs to. */
    private UUID reviewCycleId;
    /** Booking behind it. */
    private UUID bookingId;
    /** Policy lineage the uniqueness rule is scoped to. */
    private String policyLineage;
    /** Who may review whom. */
    private ReviewDirection direction;
    /** Who may write. */
    private UUID authorAccountHolderId;
    /** On whose behalf, when that is somebody else. */
    private @Nullable UUID representedAccountHolderId;
    /** Listing being reviewed, for a guest-to-listing right. */
    private @Nullable UUID subjectListingId;
    /** Person being reviewed, for a host-to-guest right. */
    private @Nullable UUID subjectAccountHolderId;
    /** Where the right stands. */
    private ReviewRightState state;
    /** When it opened. */
    private @Nullable Instant openedAt;
    /** When it closes. */
    private Instant deadlineAt;
    /** When it produced a review. */
    private @Nullable Instant exercisedAt;
    /** When it was revoked. */
    private @Nullable Instant revokedAt;
    /** Why it was revoked. */
    private @Nullable String revocationReason;
    /** Superseding booking fact that justified revocation. */
    private @Nullable UUID revocationSourceEventId;
    /** Policy version it was opened under. */
    private UUID reviewPolicyVersionId;
    /** Booking version the eligibility was read from. */
    private @Nullable Integer sourceAggregateVersion;
    /** Review it produced. */
    private @Nullable UUID reviewRecordId;
    /** Proof that a representative was authorized. */
    private @Nullable String delegationReference;
    /** How long the right record is kept. */
    private RetentionClass retentionClass;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this right may be exercised at the given instant.
     *
     * @param at instant to test
     * @return true while open and before the deadline
     */
    public boolean isExercisableAt(Instant at) {
        return state == ReviewRightState.OPEN && at.isBefore(deadlineAt);
    }

    /**
     * Whether somebody wrote on behalf of somebody else.
     *
     * @return true when an acting party is recorded alongside the principal
     */
    public boolean isDelegated() {
        return representedAccountHolderId != null
                && !representedAccountHolderId.equals(authorAccountHolderId);
    }
}
