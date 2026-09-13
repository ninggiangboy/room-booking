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
 * A command issued against an experiment or one of its epochs.
 *
 * <p>This records that somebody asked; the state of the experiment belongs to the experiment.
 * Rolling a treatment out or back needs a second person to approve, and guardrail automation may
 * pause or stop a treatment that is hurting people but may never ship one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_actions")
public class ExperimentAction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment definition this row belongs to. */
    private UUID experimentDefinitionId;
    /** The epoch acted on; absent only when archiving the experiment itself. */
    private @Nullable UUID experimentEpochId;
    /** The command being issued. */
    private ExperimentActionType actionType;
    /** Idempotency key, so a replayed command does not act twice. */
    private String commandKey;
    /** The version the caller believed it was acting on. */
    private long expectedVersion;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** Why, in words. */
    private String reasonDetail;
    /** Whether a person or guardrail automation issued it. */
    private ExperimentActorKind actorKind;
    /** Who or what issued it. */
    private String actorReference;
    /**
     * The second person who agreed; required to roll a treatment out or back, and never the
     * requester.
     */
    private @Nullable String approverReference;
    /** The guardrail that fired, required when automation acts. */
    private @Nullable UUID triggeringMetricId;
    /** Whether the command has been carried out. */
    private boolean applied;
    /** UTC instant applied. */
    private @Nullable Instant appliedAt;
    /** Why it could not be. */
    private @Nullable String failureReason;
    /** UTC instant it was requested. */
    private Instant requestedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
