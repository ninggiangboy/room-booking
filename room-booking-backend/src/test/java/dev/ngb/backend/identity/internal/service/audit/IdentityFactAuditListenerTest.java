package dev.ngb.backend.identity.internal.service.audit;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ngb.backend.identity.AccountHolderCreated;
import dev.ngb.backend.identity.CapabilityGranted;
import dev.ngb.backend.identity.SessionRevoked;
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditOutcome;
import dev.ngb.backend.platform.AuditTrailWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityFactAuditListenerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AuditTrailWriter auditTrailWriter;

    private IdentityFactAuditListener listener;

    @BeforeEach
    void setUp() {
        listener = new IdentityFactAuditListener(auditTrailWriter);
    }

    @Test
    void accountHolderCreatedIsRecordedAsASystemActedRow() {
        UUID holderId = UUID.randomUUID();

        listener.on(new AccountHolderCreated(holderId, NOW));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrailWriter).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertThat(entry.action()).isEqualTo("identity.account_holder_created");
        assertThat(entry.targetType()).isEqualTo("AccountHolder");
        assertThat(entry.targetId()).isEqualTo(holderId);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.ALLOWED);
        assertThat(entry.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.actorId()).isNull();
    }

    @Test
    void capabilityGrantedCarriesTheGrantsReasonCode() {
        UUID grantId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();

        listener.on(new CapabilityGranted(
                grantId, "PERSON", granteeId, "GUEST", "SELF_SERVICE",
                "SELF_SERVICE_REGISTRATION", NOW));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrailWriter).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertThat(entry.action()).isEqualTo("identity.capability_granted");
        assertThat(entry.targetType()).isEqualTo("CapabilityGrant");
        assertThat(entry.targetId()).isEqualTo(grantId);
        assertThat(entry.reasonCode()).isEqualTo("SELF_SERVICE_REGISTRATION");
    }

    @Test
    void sessionRevokedCarriesTheRevocationReason() {
        UUID sessionId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();

        listener.on(new SessionRevoked(sessionId, holderId, "REUSE_DETECTED", NOW));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrailWriter).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertThat(entry.action()).isEqualTo("identity.session_revoked");
        assertThat(entry.targetType()).isEqualTo("AuthSession");
        assertThat(entry.targetId()).isEqualTo(sessionId);
        assertThat(entry.reasonCode()).isEqualTo("REUSE_DETECTED");
    }
}
