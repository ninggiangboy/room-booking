package dev.ngb.backend.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.MarketContextState;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityFactsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private AuthorizationService authorizationService;

    private IdentityFacts identityFacts;
    private UUID holderId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        identityFacts = new IdentityFacts(accountHolderRepository, authorizationService, clock);
        holderId = UUID.randomUUID();
        when(authorizationService.effectiveGlobalCapabilities(holderId, NOW)).thenReturn(Set.of());
    }

    @Test
    void aPendingVerificationHolderAuthenticatesAsActive() {
        when(accountHolderRepository.findById(holderId))
                .thenReturn(Optional.of(holder(AccountHolderStatus.PENDING_VERIFICATION)));

        assertThat(identityFacts.resolve(holderId).active()).isTrue();
    }

    @Test
    void aSuspendedHolderDoesNotAuthenticate() {
        when(accountHolderRepository.findById(holderId))
                .thenReturn(Optional.of(holder(AccountHolderStatus.SUSPENDED)));

        assertThat(identityFacts.resolve(holderId).active()).isFalse();
    }

    private AccountHolder holder(AccountHolderStatus status) {
        return AccountHolder.builder()
                .id(holderId)
                .holderType(AccountHolderType.PERSON)
                .displayName("Test User")
                .status(status)
                .contextState(MarketContextState.UNRESOLVED)
                .build();
    }
}
