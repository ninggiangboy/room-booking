package dev.ngb.backend.review.internal.model.record_;

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

import dev.ngb.backend.review.internal.model.ReviewPublicProjection;


/**
 * The projection of a moderation decision that this domain needs to be correct about visibility.
 *
 * <p>The decision itself belongs to trust and safety. What is kept here is which decision was applied
 * to which exact revision, keyed by source event so a replayed event changes nothing twice.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_moderation_applications")
public class ReviewModerationApplication {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Review affected. */
    private UUID reviewRecordId;
    /** Exact revision judged. */
    private UUID reviewRevisionId;
    /** Event carrying the decision; the deduplication key. */
    private UUID sourceEventId;
    /** Decision in the owning domain. */
    private UUID moderationDecisionId;
    /** Version of that decision. */
    private int moderationDecisionVersion;
    /** Hash of the decision as applied. */
    private String decisionDigest;
    /** What the decision said to do. */
    private ModerationApplicationAction action;
    /** Which policy it was taken under. */
    private @Nullable String policyCategory;
    /** When the effect starts. */
    private Instant effectiveFrom;
    /** When it ends, for a time-bounded action. */
    private @Nullable Instant effectiveUntil;
    /** Projection this produced. */
    private ReviewPublicProjection appliedProjection;
    /** When it was applied here. */
    private Instant appliedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
