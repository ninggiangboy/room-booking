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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Append-only evidence that a privileged action was attempted, and what came of it.
 *
 * <p>There is no {@code @Version} and no {@code updatedAt} because the row is never updated: a
 * database trigger rejects {@code UPDATE} and {@code DELETE} for the application role. Correcting a
 * mistaken audit row means appending a correcting one, which is the same additive-correction rule
 * the platform applies to all historical evidence.</p>
 *
 * <p>The row carries digests and minimal structured references rather than before/after payloads.
 * Audit evidence outlives most business data, so anything sensitive recorded here would outlive the
 * lawful basis for holding it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("audit_events")
public class AuditEvent {

    /** Primary key of the audit row. */
    @Id
    private @Nullable UUID id;
    /** UTC instant the action was attempted, taken from the command's decision instant. */
    private Instant occurredAt;
    /** Action attempted, such as {@code user.suspended}. */
    private String action;
    /** Domain that owns the action and defines its evidence. */
    private String owningDomain;
    /** Kind of resource acted upon. */
    private String targetType;
    /** Identifier of that resource. */
    private UUID targetId;
    /** Whether the action was allowed, denied, failed, or partially applied. */
    private AuditOutcome outcome;
    /** Stable reason the outcome was reached. */
    private @Nullable String reasonCode;
    /** Kind of principal responsible for the attempt. */
    private ActorType actorType;
    /** Account of that principal, for a user or operator. */
    private @Nullable UUID actorId;
    /** Stable reference for a system or provider principal that has no account. */
    private @Nullable String actorReference;
    /** Organization the actor acted for, when acting under delegation. */
    private @Nullable UUID onBehalfOfOrganization;
    /** Role the delegated authority came from. */
    private @Nullable String delegatedRole;
    /** Specific permission exercised. */
    private @Nullable String delegatedPermission;
    /** Second operator who approved the action, where maker-checker applies. */
    private @Nullable UUID approverId;
    /** Request identifier of the originating call. */
    private @Nullable String requestId;
    /** Correlation identifier tying this action to its journey. */
    private String correlationId;
    /** Identifier of the command or fact that caused this action. */
    private @Nullable String causationId;
    /** ISO 3166-1 alpha-2 market whose rules governed the action. */
    private @Nullable String marketCode;
    /** Version of the policy applied when the outcome was decided. */
    private @Nullable String policyReference;
    /** SHA-256 digest of the prior state, sufficient to prove a change without storing it. */
    private @Nullable String beforeDigest;
    /** SHA-256 digest of the resulting state. */
    private @Nullable String afterDigest;
    /** Minimal structured summary of which fields changed; never their sensitive values. */
    private @Nullable JsonDocument changeSummary;
    /** Retention class decided by the policy in force when the row was written. */
    private RetentionClass retentionClass;
    /** UTC instant after which the row may be pruned; {@code null} only when retained permanently. */
    private @Nullable Instant retainUntil;
    /** Whether a legal hold suspends pruning regardless of {@link #retainUntil}. */
    private boolean legalHold;
}
