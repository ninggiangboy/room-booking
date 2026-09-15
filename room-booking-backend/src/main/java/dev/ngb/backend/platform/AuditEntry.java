package dev.ngb.backend.platform;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;


/**
 * The fields a caller supplies to record one privileged action through {@link AuditTrailWriter}.
 *
 * <p>Deliberately narrower than the {@code audit_events} table: it omits the delegation, approver,
 * policy, and digest columns that no caller in this codebase populates yet. It is a positional
 * record rather than a builder because every call site today supplies every field it needs
 * explicitly; a caller that needs one of the omitted columns can grow this record without breaking
 * the others.
 *
 * @param occurredAt UTC instant the action was attempted, taken from the command's decision instant
 * @param action action attempted, such as {@code account.suspended}
 * @param owningDomain domain that owns the action and defines its evidence, such as {@code identity}
 * @param targetType kind of resource acted upon
 * @param targetId identifier of that resource
 * @param outcome whether the action was allowed, denied, failed, or partially applied
 * @param reasonCode stable reason the outcome was reached, or {@code null} when none applies
 * @param actorType kind of principal responsible for the attempt
 * @param actorId account of that principal, or {@code null} for a principal with no account
 * @param correlationId correlation identifier tying this action to its journey, or {@code null} to
 *     have one generated
 */
public record AuditEntry(
        Instant occurredAt,
        String action,
        String owningDomain,
        String targetType,
        UUID targetId,
        AuditOutcome outcome,
        @Nullable String reasonCode,
        ActorType actorType,
        @Nullable UUID actorId,
        @Nullable String correlationId) {
}
