package dev.ngb.backend.admin.internal.model.command;

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

import dev.ngb.backend.platform.JsonDocument;


/**
 * One operator asking a domain to do something, and what the domain answered.
 * <p>A request and a recorded outcome, never a change written directly. A command that would have
 * been
 * refused when a guest issued it is refused when an operator issues it, and the refusal is the
 * outcome
 * stored here. Parameters freeze at dispatch, so what was approved is what was sent.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operational_command_executions")
public class OperationalCommandExecution {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The command version being issued, which fixes the ceiling and the approvals. */
    private UUID commandDefinitionId;
    /** The command key, denormalized because the idempotency key is unique within it. */
    private String commandKey;
    /** Caller-supplied key making a retry the same execution rather than a second one. */
    private String idempotencyKey;
    /** The account holder issuing the command. */
    private UUID actorId;
    /** The ordinary authority the command was issued under. */
    private @Nullable UUID roleAssignmentId;
    /** The emergency authority it was issued under instead. */
    private @Nullable UUID breakGlassGrantId;
    /** The domain that owns the target. */
    private String targetDomain;
    /** Kind of thing being acted on; it must match what the command declares. */
    private String targetType;
    /** The row being acted on. */
    private @Nullable UUID targetId;
    /** Reference to the target, for things with no row of their own. */
    private @Nullable String targetReference;
    /** ISO 3166-1 alpha-2 market the target belongs to. */
    private @Nullable String marketCode;
    /** What the command was asked to do, as the domain will read it. */
    private JsonDocument parameters;
    /** Digest of the parameters, so an approval is of exactly what was sent. */
    private String parametersDigest;
    /** Amount the command moves, in integer minor units of its currency. */
    private @Nullable Long amountMinor;
    /** ISO 4217 alphabetic code the amount is denominated in. */
    private @Nullable String amountCurrency;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String reasonCode;
    /** What the operator wrote, for a command that demands more than a code. */
    private @Nullable String justification;
    /** Whether this asks what would happen rather than making it happen. */
    private boolean dryRun;
    /** UTC instant the command was issued. */
    private Instant requestedAt;
    /** Where the command stands between being asked for and being answered. */
    private CommandDispatchState dispatchState;
    /** UTC instant the last required approval arrived. */
    private @Nullable Instant approvedAt;
    /** UTC instant it was handed to its domain. */
    private @Nullable Instant dispatchedAt;
    /** UTC instant the domain answered. */
    private @Nullable Instant completedAt;
    /** What the owning domain did with it; a refusal is a normal outcome, not a gap. */
    private @Nullable CommandDomainOutcome domainOutcome;
    /** Reference to what the domain created or changed. */
    private @Nullable String domainReference;
    /** Why the domain refused, in its own words. */
    private @Nullable String refusalReason;
    /** Why the command could not be completed at all. */
    private @Nullable String failureReason;
    /** Digest of the target before the command, so the audit row is a before and after. */
    private @Nullable String beforeDigest;
    /** Digest of the target afterwards. */
    private @Nullable String afterDigest;
    /** The shared audit row this execution also wrote. */
    private @Nullable UUID auditEventId;
    /** Opaque identifier tying the execution to the rest of one operator journey. */
    private String correlationId;
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
