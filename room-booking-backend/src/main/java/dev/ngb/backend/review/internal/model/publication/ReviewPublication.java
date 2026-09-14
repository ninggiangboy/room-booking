package dev.ngb.backend.review.internal.model.publication;

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
import dev.ngb.backend.platform.ActorType;

/**
 * When a review was visible, as an interval rather than a timestamp.
 *
 * <p>Removing and restoring produce two rows, so the history of what the public could see is
 * reconstructible. An exclusion constraint refuses overlapping intervals for one review, and a
 * trigger refuses any interval that starts before its cycle revealed -- a publication written a
 * moment early tells the counterpart what they were not meant to know.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_publications")
public class ReviewPublication {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Review that was published. */
    private UUID reviewRecordId;
    /** Exact revision that was shown. */
    private UUID reviewRevisionId;
    /** Cycle whose reveal authorized it. */
    private UUID reviewCycleId;
    /** Reveal epoch it was published under. */
    private int revealVersion;
    /** When it became visible. */
    private Instant publishedFrom;
    /** When it stopped being visible. */
    private @Nullable Instant publishedUntil;
    /** Why the interval opened. */
    private PublicationReason publicationReason;
    /** Why it closed. */
    private @Nullable String retractionReason;
    /** Public rendering held elsewhere. */
    private @Nullable String publicDerivativeReference;
    /** Redaction applied to that rendering. */
    private @Nullable String redactionReference;
    /** Moderation decision that qualified it. */
    private @Nullable UUID moderationDecisionId;
    /** Version of that decision. */
    private @Nullable Integer moderationDecisionVersion;
    /** Policy version in force. */
    private UUID reviewPolicyVersionId;
    /** Event that told the aggregates about it. */
    private @Nullable UUID aggregateEventId;
    /** What kind of actor opened the interval. */
    private ActorType actorType;
    /** Which account holder, where there was one. */
    private @Nullable UUID actorAccountHolderId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this interval is the one currently in effect.
     *
     * @return true while it has no end
     */
    public boolean isOpen() {
        return publishedUntil == null;
    }

    /**
     * Whether the review was visible at the given instant.
     *
     * @param at instant to test
     * @return true when the instant falls inside this half-open interval
     */
    public boolean coversInstant(Instant at) {
        return !at.isBefore(publishedFrom) && (publishedUntil == null || at.isBefore(publishedUntil));
    }
}
