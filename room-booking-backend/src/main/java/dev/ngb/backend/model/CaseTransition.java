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
import org.springframework.data.relational.core.mapping.Table;

/**
 * Append-only record of one state change, carrying the version the actor expected to act on.
 *
 * <p>A unique command identity per case makes a retried transition replay rather than fire twice, which
 * matters because a transition carries an SLA effect and an outbound event.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_transitions")
public class CaseTransition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Position of this row within its parent, unique there. */
    private long sequenceNumber;
    /** Where the from stands. */
    private @Nullable SupportCaseState fromState;
    /** Where the to stands. */
    private SupportCaseState toState;
    /** The command this row belongs to. */
    private String commandId;
    /** Which version of the expected case applies. */
    private long expectedCaseVersion;
    /** Which version of the transition policy applies. */
    private int transitionPolicyVersion;
    /** Which actor type this row carries. */
    private TransitionActorType actorType;
    /** The actor account holder this row belongs to. */
    private @Nullable UUID actorAccountHolderId;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** Stable key naming the explanation template. */
    private @Nullable String explanationTemplateKey;
    /** The related work item this row belongs to. */
    private @Nullable UUID relatedWorkItemId;
    /** The related decision this row belongs to. */
    private @Nullable UUID relatedDecisionId;
    /** SLA effect. */
    private SlaEffect slaEffect;
    /** The outbox event this row belongs to. */
    private @Nullable UUID outboxEventId;
    /** UTC instant occurred. */
    private Instant occurredAt;
    /** UTC instant committed. */
    private Instant committedAt;
    /** The correlation this row belongs to. */
    private @Nullable UUID correlationId;
    /** The causation this row belongs to. */
    private @Nullable UUID causationId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
