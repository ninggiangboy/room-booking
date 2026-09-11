package dev.ngb.backend.repository;

import dev.ngb.backend.model.AuditEvent;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Appends and reads tamper-evident evidence of privileged actions.
 *
 * <p>Only the inherited insert and read operations are usable. A database trigger rejects
 * {@code UPDATE} and {@code DELETE} on {@code audit_events} for the application role, so calling
 * {@code save} on a row that already has an ID, or any {@code delete} method, fails at the database
 * rather than silently rewriting evidence. Correcting a mistaken row means appending a correcting
 * one.</p>
 */
public interface AuditEventRepository extends ListCrudRepository<AuditEvent, UUID> {

    /**
     * Returns the audit timeline for one resource, most recent first.
     *
     * <p>Spring derives two equality predicates from the property path and descending ordering from
     * the {@code OrderByOccurredAtDesc} suffix:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM audit_events
     * WHERE target_type = ?
     *   AND target_id = ?
     * ORDER BY occurred_at DESC
     * }</pre>
     *
     * <p>This is the query behind "what was done to this booking, and by whom".</p>
     *
     * @param targetType kind of resource
     * @param targetId identifier of that resource
     * @return possibly empty timeline, most recent first
     */
    List<AuditEvent> findAllByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            String targetType,
            UUID targetId);

    /**
     * Returns what one principal did, most recent first.
     *
     * <p>Spring derives {@code WHERE actor_id = ? ORDER BY occurred_at DESC}. Used when reviewing an
     * operator's activity or investigating a compromised account.</p>
     *
     * @param actorId account of the principal
     * @return possibly empty timeline, most recent first
     */
    List<AuditEvent> findAllByActorIdOrderByOccurredAtDesc(UUID actorId);

    /**
     * Returns every audited action recorded for one journey.
     *
     * <p>Spring derives {@code WHERE correlation_id = ? ORDER BY occurred_at ASC}, reconstructing a
     * single request's privileged actions across domains in the order they happened.</p>
     *
     * @param correlationId correlation identifier of the journey
     * @return possibly empty timeline, oldest first
     */
    List<AuditEvent> findAllByCorrelationIdOrderByOccurredAtAsc(String correlationId);
}
