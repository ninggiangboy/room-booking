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
 * A bounded window within which result ordering is stable.
 *
 * <p>A cursor issued on page one must still mean the same thing on page four. Rotation is useful and
 * pure randomness is not, so the tie-breaker seed lives here, which makes the rotation both
 * deliberate and reproducible.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ranking_epochs")
public class RankingEpoch {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming this epoch, which cursors are bound to. */
    private String epochKey;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The ranking policy version this row belongs to. */
    private UUID rankingPolicyVersionId;
    /** The ranking model version this row belongs to. */
    private @Nullable UUID rankingModelVersionId;
    /** Seed for the stable tie-breaker, so rotation is deliberate and reproducible rather than random. */
    private long tieBreakerSeed;
    /** Where the epoch stands. */
    private RankingEpochState state;
    /** UTC instant the epoch opened. */
    private Instant openedAt;
    /** UTC instant it is scheduled to close. */
    private Instant closesAt;
    /** UTC instant it actually closed. */
    private @Nullable Instant closedAt;
    /** Why it was pulled out from under live cursors; required for an invalidated epoch. */
    private @Nullable String invalidationReason;
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
