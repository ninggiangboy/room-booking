package dev.ngb.backend.platform.internal.service.audit;

import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.RetentionClass;
import dev.ngb.backend.platform.internal.model.audit.AuditEvent;


/**
 * Constructs append-only evidence rows from a caller-supplied {@link AuditEntry}.
 *
 * <p>{@code @Component} makes the factory injectable. Package-private visibility keeps
 * construction of this evidence row inside the platform module's audit package; every other module
 * reaches it only through {@link dev.ngb.backend.platform.AuditTrailWriter}. Never reads a clock —
 * the entry already carries the caller's own decision instant.</p>
 */
@Component
class AuditEventFactory {

    /**
     * Builds an unsaved audit row from the caller's entry.
     *
     * <p>A correlation identifier is generated when the caller did not supply one, since every row
     * must have one to support {@code findAllByCorrelationIdOrderByOccurredAtAsc}.</p>
     *
     * @param entry fields supplied by the caller
     * @param retentionClass configured default retention class for rows this writer appends
     * @param retention positive duration added to {@code entry.occurredAt()} to obtain the
     *     retention deadline; ignored when {@code retentionClass} is {@link RetentionClass#PERMANENT}
     * @return audit row to persist
     */
    AuditEvent create(AuditEntry entry, RetentionClass retentionClass, Duration retention) {
        return AuditEvent.builder()
                // No client-generated id: AuditEvent has no @Version (append-only, enforced by a
                // database trigger instead), so Spring Data JDBC's isNew() check falls back to "is
                // the @Id null". A pre-assigned id would make it issue a silent, zero-row UPDATE
                // instead of an INSERT. The database's own DEFAULT gen_random_uuid() generates it.
                .occurredAt(entry.occurredAt())
                .action(entry.action())
                .owningDomain(entry.owningDomain())
                .targetType(entry.targetType())
                .targetId(entry.targetId())
                .outcome(entry.outcome())
                .reasonCode(entry.reasonCode())
                .actorType(entry.actorType())
                .actorId(entry.actorId())
                .correlationId(
                        entry.correlationId() != null
                                ? entry.correlationId()
                                : UUID.randomUUID().toString())
                .retentionClass(retentionClass)
                .retainUntil(
                        retentionClass == RetentionClass.PERMANENT
                                ? null
                                : entry.occurredAt().plus(retention))
                .legalHold(false)
                .build();
    }
}
