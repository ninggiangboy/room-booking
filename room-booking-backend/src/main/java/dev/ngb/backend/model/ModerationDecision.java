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
 * The outcome for one content revision, with the visibility instruction its owning domain carries out.
 *
 * <p>Frozen once written; removing or restoring later is a new decision naming the one it supersedes,
 * so a moderation history stays readable instead of collapsing into a row whose current value nobody
 * can account for. One effective decision per revision. A decision that makes content visible must
 * name the latest revision of its item -- an old approval cannot publish a new revision -- while a
 * removal or a safety escalation may always name an older one, because those are about what was said
 * then. An escalation leaves visibility untouched: routing a threat is not the same as deciding
 * whether the words stay up.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("moderation_decisions")
public class ModerationDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The revision decided about. */
    private UUID contentRevisionId;
    /** What was decided. */
    private ModerationOutcome outcome;
    /** What the owning domain is asked to do. */
    private ModerationVisibilityInstruction visibilityInstruction;
    /** Policy version applied. */
    private @Nullable UUID riskPolicyId;
    /** The specific rule within it. */
    private @Nullable String policyReference;
    /** Machine-readable reasons; never shown to the author. */
    private String[] internalReasons;
    /** The approved family the author may be told; required for an adverse outcome. */
    private @Nullable String userReasonFamily;
    /** Where the replaced spans are recorded; present exactly for a mask. */
    private @Nullable String spanMapReference;
    /** Whether automation or a person decided. */
    private ModerationDeciderType deciderType;
    /** The person, where a person decided. */
    private @Nullable UUID decidedByAccountHolderId;
    /** The review task behind it. */
    private @Nullable UUID riskReviewTaskId;
    /** Whether it still stands. */
    private ModerationDecisionState state;
    /** When a quarantine must be answered by; required for one. */
    private @Nullable Instant reviewDeadlineAt;
    /** When the decision stops applying. */
    private @Nullable Instant expiresAt;
    /** The decision this one replaces. */
    private @Nullable UUID supersedesDecisionId;
    /** The decision that replaced this one. */
    private @Nullable UUID supersededByDecisionId;
    /** When it was decided. */
    private Instant decidedAt;
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
