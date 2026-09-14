package dev.ngb.backend.trust.internal.model.review;

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
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.trust.internal.model.RiskActionTier;

/**
 * Where human risk work waits, and who may take it.
 *
 * <p>Routing by skill, tier and market is what keeps a severe safety report out of a general fraud
 * queue.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_review_queues")
public class RiskReviewQueue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key; unique. */
    private String queueKey;
    /** Name shown to reviewers. */
    private String displayName;
    /** What a reviewer must be qualified in to claim from it. */
    private ReviewerSkill requiredSkill;
    /** Lowest tier this queue accepts. */
    private RiskActionTier minimumActionTier;
    /** Market it serves, where it is market specific. */
    private @Nullable UUID marketId;
    /** Language it serves, where it is language specific. */
    private @Nullable String languageTag;
    /** The response target work in it is measured against. */
    private int targetResponseSeconds;
    /** Where work escalates to. */
    private @Nullable UUID escalationQueueId;
    /** Rules describing who may not review what. */
    private @Nullable JsonDocument conflictRules;
    /** Whether the queue accepts work. */
    private ReviewQueueStatus status;
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
