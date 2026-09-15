package dev.ngb.backend.identity.internal.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

import dev.ngb.backend.identity.internal.model.session.AuthSession;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import dev.ngb.backend.platform.AssuranceLevel;


/**
 * Immutable API projection of one live session, for the "your devices" list.
 *
 * @param id session identifier, used to target a single revoke
 * @param clientDescriptor client description captured at sign-in, or {@code null} when unavailable
 * @param authenticationMethod how the principal proved who they were when the session opened
 * @param assuranceLevel strength of that proof
 * @param lastUsedAt UTC instant the session was last exercised
 * @param createdAt UTC instant the session was opened
 */
public record SessionResponse(
        UUID id,
        @Nullable String clientDescriptor,
        AuthenticationMethod authenticationMethod,
        AssuranceLevel assuranceLevel,
        Instant lastUsedAt,
        Instant createdAt) {

    /**
     * Copies a persistence entity into a safe response, omitting origin hashes and token linkage.
     *
     * @param session persistence entity to project
     * @return immutable session response
     */
    public static SessionResponse from(AuthSession session) {
        return new SessionResponse(
                session.getId(),
                session.getClientDescriptor(),
                session.getAuthenticationMethod(),
                session.getAssuranceLevel(),
                session.getLastUsedAt(),
                session.getCreatedAt());
    }
}
