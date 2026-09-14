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
import dev.ngb.backend.platform.GovernedRegistryStatus;

/**
 * One thing an operator is allowed to ask the marketplace to do.
 * <p>Names the permission that opens it, whether it moves money, whether it can be undone and what
 * it
 * costs at most. What it does not describe is how to perform the action: the domain that owns the
 * booking, the payment or the payout still performs it under its own invariants.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operational_command_definitions")
public class OperationalCommandDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the command, the same across every version of it. */
    private String commandKey;
    /** Which version of this command key the row is. */
    private int commandVersion;
    /** Human-readable name shown in the administrative console. */
    private String displayName;
    /** The domain that performs the command and applies its own invariants to it. */
    private String owningDomain;
    /** What the command does to its target. */
    private CommandActionKind actionKind;
    /** Kind of thing the command acts on. */
    private String targetType;
    /** The permission an operator must hold to issue it. */
    private String requiredPermissionKey;
    /** Whether issuing it moves money. */
    private boolean monetary;
    /** Largest amount one execution may move, in integer minor units of its currency. */
    private @Nullable Long maximumAmountMinor;
    /** ISO 4217 alphabetic code the ceiling is denominated in. */
    private @Nullable String amountCurrency;
    /** Whether the effect can be undone by another command. */
    private boolean reversible;
    /** The command that undoes this one. */
    private @Nullable String reversalCommandKey;
    /** Whether an execution needs a second person; money and one-way doors always do. */
    private boolean requiresMakerChecker;
    /** Which approval roles have to sign off on an execution. */
    private @Nullable String[] requiredApprovalRoles;
    /** Whether every execution has to carry an approved reason code. */
    private boolean requiresReasonCode;
    /** Which set of approved reason codes applies. */
    private @Nullable String reasonCodeSet;
    /** Whether every execution has to carry a written justification. */
    private boolean requiresJustification;
    /** Whether the command can be asked what it would do without doing it. */
    private boolean dryRunSupported;
    /** How many executions a day are permitted before the command is throttled. */
    private @Nullable Integer dailyExecutionLimit;
    /** The team accountable for the command, held in the directory rather than here. */
    private String ownerReference;
    /** Where the command is documented. */
    private String documentationReference;
    /** Where the command stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the command became available. */
    private @Nullable Instant activatedAt;
    /** UTC instant the command was marked for replacement. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant the command stopped being available. */
    private @Nullable Instant retiredAt;
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
