package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.session.AuthSession;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
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
     * @return session row to persist, with no current token set yet
     */
    AuthSession create(
            UUID accountHolderId,
            AuthenticationMethod method,
            AssuranceLevel assuranceLevel,
            Instant issuedAt,
            Duration idleTtl,
            Duration absoluteTtl) {
        return AuthSession.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .authenticationMethod(method)
                .assuranceLevel(assuranceLevel)
                .lastAssuranceProofAt(issuedAt)
                .rotationGeneration(0)
                .lastUsedAt(issuedAt)
                .idleExpiresAt(issuedAt.plus(idleTtl))
                .absoluteExpiresAt(issuedAt.plus(absoluteTtl))
                .build();
    }
}
