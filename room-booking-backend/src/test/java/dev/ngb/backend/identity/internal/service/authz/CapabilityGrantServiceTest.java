package dev.ngb.backend.identity.internal.service.authz;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import dev.ngb.backend.identity.CapabilityGranted;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;
import dev.ngb.backend.identity.internal.model.capability.GrantSource;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityGrantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityGrantServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private CapabilityGrantRepository capabilityGrantRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CapabilityGrantService service;

    @BeforeEach
    void setUp() {
        service = new CapabilityGrantService(capabilityGrantRepository, eventPublisher);
    }

    @Test
    void issuingARoleGrantPublishesCapabilityGranted() {
        UUID granteeId = UUID.randomUUID();
        when(capabilityGrantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CapabilityGrant grant = service.issueRoleGrant(
                PrincipalType.PERSON, granteeId, RoleBundle.GUEST, GrantSource.SELF_SERVICE,
                "SELF_SERVICE_REGISTRATION", NOW);

        ArgumentCaptor<CapabilityGranted> captor = ArgumentCaptor.forClass(CapabilityGranted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CapabilityGranted event = captor.getValue();
        assertThat(event.grantId()).isEqualTo(grant.getId());
        assertThat(event.granteeType()).isEqualTo("PERSON");
        assertThat(event.granteeId()).isEqualTo(granteeId);
        assertThat(event.roleName()).isEqualTo("GUEST");
        assertThat(event.source()).isEqualTo("SELF_SERVICE");
        assertThat(event.reasonCode()).isEqualTo("SELF_SERVICE_REGISTRATION");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }
}
