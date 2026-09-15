package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.session.AuthSession;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import dev.ngb.backend.platform.AssuranceLevel;


/**
 * Constructs a new durable sign-in at generation zero.
 *
 * <p>{@code @Component} makes the factory injectable. Package-private visibility keeps
 * construction of this aggregate inside the authentication package. The caller supplies its own
 * decision instant so a workflow that also creates the session's first token stamps every row
 * with one instant instead of several separate clock reads.</p>
 */
@Component
class AuthSessionFactory {

    /**
     * Builds an unsaved session with no rotations performed yet.
     *
     * @param accountHolderId account the session belongs to
     * @param method how the principal proved who they were when the session opened
     * @param assuranceLevel strength of that proof
     * @param issuedAt workflow's single decision instant
     * @param idleTtl positive duration added to {@code issuedAt} to obtain the idle expiry
     * @param absoluteTtl positive duration added to {@code issuedAt} to obtain the absolute expiry
     * @param clientDescriptor client description shown to the owner when listing their devices, or
     *     {@code null} when unavailable
     * @param originHash SHA-256 digest of the network origin, or {@code null} when unavailable
     * @return session row to persist, with no current token set yet
     */
    AuthSession create(
            UUID accountHolderId,
            AuthenticationMethod method,
            AssuranceLevel assuranceLevel,
            Instant issuedAt,
            Duration idleTtl,
            Duration absoluteTtl,
            @Nullable String clientDescriptor,
            @Nullable String originHash) {
        return AuthSession.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .authenticationMethod(method)
                .assuranceLevel(assuranceLevel)
                .lastAssuranceProofAt(issuedAt)
                .clientDescriptor(clientDescriptor)
                .originHash(originHash)
                .rotationGeneration(0)
                .lastUsedAt(issuedAt)
                .idleExpiresAt(issuedAt.plus(idleTtl))
                .absoluteExpiresAt(issuedAt.plus(absoluteTtl))
                .build();
    }
}
