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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;


/**
 * One person marking one review helpful.
 *
 * <p>The review author is denormalized onto the row so that voting for yourself is refused by the
 * database and not only by a service that might forget.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_helpful_votes")
public class ReviewHelpfulVote {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Review voted on. */
    private UUID reviewRecordId;
    /** Who voted. */
    private UUID voterAccountHolderId;
    /** Author of that review, kept here for the self-vote check. */
    private UUID reviewAuthorAccountHolderId;
    /** Whether the vote still counts. */
    private HelpfulVoteState state;
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
