package dev.ngb.backend.identity.internal.service.audit;

import dev.ngb.backend.identity.AccountHolderCreated;
import dev.ngb.backend.identity.CapabilityGranted;
import dev.ngb.backend.identity.SessionRevoked;
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditOutcome;
import dev.ngb.backend.platform.AuditTrailWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records {@code identity.*}-prefixed audit rows for this module's own published facts.
 *
 * <p>{@code @Component} registers the bean; Lombok generates constructor injection for the writer.
 * Each method is a separate {@code @ApplicationModuleListener}, which composes {@code @Async},
 * {@code @Transactional(propagation = REQUIRES_NEW)}, and {@code @TransactionalEventListener} — see
 * this package's Javadoc and {@code docs/architecture/event-publication-registry.md}. Every action
 * name here is prefixed {@code identity.} specifically so it reads as this listener's own coarse
 * record of an event, distinct from the finer, actor-attributed {@code account.*}/{@code
 * session.*}/{@code organization.*} rows other call sites already write inline for the same
 * underlying change.</p>
 */
@Component
@RequiredArgsConstructor
class IdentityFactAuditListener {

    private final AuditTrailWriter auditTrailWriter;

    /**
     * Records that a new account holder was created.
     *
     * @param event the published fact
     */
    @ApplicationModuleListener
    void on(AccountHolderCreated event) {
        auditTrailWriter.record(new AuditEntry(
                event.occurredAt(),
                "identity.account_holder_created",
                "identity",
                "AccountHolder",
                event.accountHolderId(),
                AuditOutcome.ALLOWED,
                null,
                ActorType.SYSTEM,
                null,
                null));
    }

    /**
     * Records that a new capability grant was issued.
     *
     * @param event the published fact
     */
    @ApplicationModuleListener
    void on(CapabilityGranted event) {
        auditTrailWriter.record(new AuditEntry(
                event.occurredAt(),
                "identity.capability_granted",
                "identity",
                "CapabilityGrant",
                event.grantId(),
                AuditOutcome.ALLOWED,
                event.reasonCode(),
                ActorType.SYSTEM,
                null,
                null));
    }

    /**
     * Records that a session was revoked.
     *
     * @param event the published fact
     */
    @ApplicationModuleListener
    void on(SessionRevoked event) {
        auditTrailWriter.record(new AuditEntry(
                event.occurredAt(),
                "identity.session_revoked",
                "identity",
                "AuthSession",
                event.sessionId(),
                AuditOutcome.ALLOWED,
                event.reason(),
                ActorType.SYSTEM,
                null,
                null));
    }
}
