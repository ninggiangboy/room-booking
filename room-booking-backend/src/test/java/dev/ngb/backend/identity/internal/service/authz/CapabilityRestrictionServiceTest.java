package dev.ngb.backend.identity.internal.service.authz;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ngb.backend.identity.internal.exception.CapabilityRestrictionNotFoundException;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityRestrictionRepository;
import dev.ngb.backend.platform.AuditTrailWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityRestrictionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private CapabilityRestrictionRepository capabilityRestrictionRepository;
    @Mock
    private CapabilityRestrictionFactory capabilityRestrictionFactory;
    @Mock
    private AuditTrailWriter auditTrailWriter;

    private CapabilityRestrictionService service;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        service = new CapabilityRestrictionService(
                capabilityRestrictionRepository, capabilityRestrictionFactory, auditTrailWriter);
        adminId = UUID.randomUUID();
    }

    @Test
    void issuingBuildsThroughTheFactoryAndAudits() {
        UUID principalId = UUID.randomUUID();
        CapabilityRestriction built = restriction(null);
        when(capabilityRestrictionFactory.create(
                PrincipalType.PERSON, principalId, Capability.BOOKING_CREATE,
                AuthorizationScopeType.GLOBAL, null, "RISK_REVIEW", null, NOW, null))
                .thenReturn(built);
        when(capabilityRestrictionRepository.save(built)).thenReturn(built);

        CapabilityRestriction result = service.issue(
                PrincipalType.PERSON, principalId, Capability.BOOKING_CREATE,
                AuthorizationScopeType.GLOBAL, null, "RISK_REVIEW", null, null, null, adminId, NOW);

        assertThat(result).isSameAs(built);
        verify(auditTrailWriter).record(any());
    }

    @Test
    void liftingAnActiveRestrictionRecordsWhoAndWhen() {
        CapabilityRestriction restriction = restriction(null);
        when(capabilityRestrictionRepository.findById(restriction.getId()))
                .thenReturn(Optional.of(restriction));
        when(capabilityRestrictionRepository.save(restriction)).thenReturn(restriction);

        CapabilityRestriction result = service.lift(restriction.getId(), adminId, NOW);

        assertThat(result.getLiftedBy()).isEqualTo(adminId);
        assertThat(result.getLiftedAt()).isEqualTo(NOW);
        verify(auditTrailWriter).record(any());
    }

    @Test
    void liftingAnAlreadyLiftedRestrictionIsIdempotent() {
        UUID firstLifter = UUID.randomUUID();
        Instant firstLiftedAt = NOW.minusSeconds(60);
        CapabilityRestriction restriction = restriction(null);
        restriction.setLiftedBy(firstLifter);
        restriction.setLiftedAt(firstLiftedAt);
        when(capabilityRestrictionRepository.findById(restriction.getId()))
                .thenReturn(Optional.of(restriction));

        CapabilityRestriction result = service.lift(restriction.getId(), adminId, NOW);

        assertThat(result.getLiftedBy()).isEqualTo(firstLifter);
        assertThat(result.getLiftedAt()).isEqualTo(firstLiftedAt);
        verify(capabilityRestrictionRepository, never()).save(any());
    }

    @Test
    void liftingAnUnknownRestrictionFails() {
        UUID restrictionId = UUID.randomUUID();
        when(capabilityRestrictionRepository.findById(restrictionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lift(restrictionId, adminId, NOW))
                .isInstanceOf(CapabilityRestrictionNotFoundException.class);
    }

    private static CapabilityRestriction restriction(Instant liftedAt) {
        return CapabilityRestriction.builder()
                .id(UUID.randomUUID())
                .principalType(PrincipalType.PERSON)
                .principalId(UUID.randomUUID())
                .capability(Capability.BOOKING_CREATE.name())
                .scopeType(AuthorizationScopeType.GLOBAL)
                .reasonCode("RISK_REVIEW")
                .effectiveFrom(NOW)
                .liftedAt(liftedAt)
                .build();
    }
}
