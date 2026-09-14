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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.ngb.backend.review.internal.model.ReviewModerationState;
import dev.ngb.backend.review.internal.model.ReviewPublicProjection;

/**
 * A host's public answer to a published review.
 *
 * <p>One live response per review under the single-response model, so a rating cannot be buried under
 * a thread. Its text lives in insert-only revisions for the same reason the review's does.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_responses")
public class ReviewResponse {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Review being answered. */
    private UUID reviewRecordId;
    /** Listing the responder speaks for. */
    private @Nullable UUID listingId;
    /** Host answering. */
    private UUID responderAccountHolderId;
    /** Who physically wrote it, when that is somebody else. */
    private @Nullable UUID actingAccountHolderId;
    /** Where the response stands. */
    private ReviewResponseState state;
    /** When the response window closes. */
    private @Nullable Instant responseDeadlineAt;
    /** When it was submitted. */
    private @Nullable Instant submittedAt;
    /** When it was withdrawn. */
    private @Nullable Instant withdrawnAt;
    /** Why it was withdrawn. */
    private @Nullable String withdrawalReason;
    /** Revision standing as the response. */
    private @Nullable UUID submittedRevisionId;
    /** Highest revision number written. */
    private int latestRevisionNumber;
    /** What moderation says about it. */
    private ReviewModerationState moderationState;
    /** What the public can see of it. */
    private ReviewPublicProjection publicProjection;
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
