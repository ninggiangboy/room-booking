package dev.ngb.backend.platform.internal.service.audit;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditTrailWriter;
import dev.ngb.backend.platform.RetentionClass;
import dev.ngb.backend.platform.internal.model.audit.AuditEvent;
import dev.ngb.backend.platform.internal.repository.audit.AuditEventRepository;
import dev.ngb.backend.platform.util.DurationUtils;


/**
 * The only writer of {@code audit_events} reachable from outside the platform module.
 *
 * <p>{@code @Service} registers this as the {@link AuditTrailWriter} bean Spring injects wherever
 * the port is depended on, matching {@code EmailSender}/{@code SmtpEmailSender}. The explicit
 * constructor, rather than Lombok, validates the configured retention while accepting
 * {@code @Value} property injection, matching {@code RefreshTokenService}.</p>
 *
 * <p>Every row this writer appends is stamped {@link RetentionClass#STANDARD}. Callers that need a
 * different retention class do not exist yet in this codebase; when one does, this class is the
 * only place that decision needs to be threaded through.</p>
 */
@Service
class AuditTrailWriterImpl implements AuditTrailWriter {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventFactory auditEventFactory;
    private final Duration retention;

    /**
     * Creates a writer with a validated default retention period.
     *
     * @param auditEventRepository persistence gateway for audit rows
     * @param auditEventFactory builder of audit rows
     * @param retention configured positive duration a {@link RetentionClass#STANDARD} row is
     *     retained before it becomes eligible for pruning
     */
    AuditTrailWriterImpl(
            AuditEventRepository auditEventRepository,
            AuditEventFactory auditEventFactory,
            @Value("${app.audit.retention:730d}") Duration retention) {
        this.auditEventRepository = auditEventRepository;
        this.auditEventFactory = auditEventFactory;
        this.retention = DurationUtils.requirePositive(retention, "audit retention");
    }

    @Override
    public void record(AuditEntry entry) {
        AuditEvent event = auditEventFactory.create(entry, RetentionClass.STANDARD, retention);
        auditEventRepository.save(event);
    }
}
