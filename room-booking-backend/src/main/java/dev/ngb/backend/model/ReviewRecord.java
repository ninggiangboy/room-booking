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
 * The aggregate identity of one review.
 *
 * <p>It holds no text. The words live in immutable revisions, and this row holds which revision is
 * currently submitted plus the five independent dimensions that decide what anybody may see.
 * Collapsing those into one status would produce an unreviewable cross-product of states.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_records")
public class ReviewRecord {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Right this review was written under. */
    private UUID reviewRightId;
    /** Cycle governing its disclosure. */
    private UUID reviewCycleId;
    /** Booking behind it. */
    private UUID bookingId;
    /** Listing it concerns, where it concerns one. */
    private @Nullable UUID listingId;
    /** Who reviewed whom. */
    private ReviewDirection direction;
    /** Who wrote it. */
    private UUID authorAccountHolderId;
    /** On whose behalf, when that is somebody else. */
    private @Nullable UUID representedAccountHolderId;
    /** Listing reviewed, for a guest-to-listing review. */
    private @Nullable UUID subjectListingId;
    /** Person reviewed, for a host-to-guest review. */
    private @Nullable UUID subjectAccountHolderId;
    /** Revision currently standing as the submission. */
    private @Nullable UUID submittedRevisionId;
    /** Highest revision number written. */
    private int latestRevisionNumber;
    /** What the author has done. */
    private ReviewAuthoringState authoringState;
    /** Where the cycle has got to for this review. */
    private ReviewDisclosureState disclosureState;
    /** What moderation says. */
    private ReviewModerationState moderationState;
    /** What the public can see. */
    private ReviewPublicProjection publicProjection;
    /** Whether derived intelligence may read it. */
    private ReviewIntelligenceEligibility intelligenceEligibility;
    /** When it was submitted. */
    private @Nullable Instant submittedAt;
    /** When the author withdrew it. */
    private @Nullable Instant withdrawnAt;
    /** Why they withdrew it. */
    private @Nullable String withdrawalReason;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether deletion is barred. */
    private boolean legalHold;
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
     * Whether the public can currently see this review.
     *
     * @return true for a public or redacted projection
     */
    public boolean isPublic() {
        return publicProjection == ReviewPublicProjection.PUBLIC
                || publicProjection == ReviewPublicProjection.PUBLIC_REDACTED;
    }

    /**
     * Whether this review counts towards ratings and derived intelligence.
     *
     * <p>Both dimensions are asked: a review can be publicly visible and excluded from
     * intelligence, or included and not yet shown.</p>
     *
     * @return true when submitted, included, and not withdrawn
     */
    public boolean countsTowardsAggregates() {
        return authoringState == ReviewAuthoringState.SUBMITTED_FINAL
                && intelligenceEligibility == ReviewIntelligenceEligibility.INCLUDED;
    }
}
