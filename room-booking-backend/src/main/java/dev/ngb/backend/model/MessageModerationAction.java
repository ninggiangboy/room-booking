package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * What moderation did to a message, and on whose authority.
 *
 * <p>A classifier score is a proposal, never an invisible final decision: the database refuses to
 * record a restriction or a safety escalation attributed to a model alone. Every intervention
 * carries its policy version, reason code, explanation class and appeal route, because that is what
 * an appeal has to be answered with.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("message_moderation_actions")
public class MessageModerationAction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Message the action applies to. */
    private UUID messageId;
    /** Which version of the content was assessed; zero means the original. */
    private short contentRevisionNumber;
    /** Policy that mapped evidence to this action. */
    private String policyReference;
    /** Version of that policy. */
    private Integer policyVersion;
    /** Classifier that produced a score, when one did. */
    private @Nullable String modelKey;
    /** Version of that classifier. */
    private @Nullable String modelVersion;
    /** Classifier score between zero and one. */
    private @Nullable BigDecimal riskScore;
    /** What was done. */
    private ModerationAction action;
    /** Whose authority the action rests on. */
    private ModerationDecider decidedBy;
    /** The reviewer, required when a person decided. */
    private @Nullable UUID reviewerAccountHolderId;
    /** Why, in the vocabulary shown to the author. */
    private String reasonCode;
    /** How much of the reasoning may be disclosed. */
    private String explanationClass;
    /** Evidence the decision cites. */
    private @Nullable String evidenceReference;
    /** Whether the action can be, or has been, appealed. */
    private ModerationAppealState appealState;
    /** When an appeal was requested. */
    private @Nullable Instant appealedAt;
    /** When the appeal was decided. */
    private @Nullable Instant appealResolvedAt;
    /** When the action was taken. */
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

    /**
     * Whether this action is one a classifier may not take alone.
     *
     * @return {@code true} for restriction and safety escalation
     */
    public boolean isConsequential() {
        return action == ModerationAction.RESTRICT || action == ModerationAction.SAFETY_ESCALATE;
    }
}
