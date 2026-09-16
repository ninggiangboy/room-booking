package dev.ngb.backend.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event marking that one {@code auth_sessions} row was revoked.
 *
 * <p>Carries no secret, so it is published through Spring Modulith's durable
 * {@code @ApplicationModuleListener} registry rather than a bare listener — see
 * {@code docs/architecture/event-publication-registry.md}. Published once per session by the
 * single choke point every revocation path (self-service, administrator suspension, and reuse
 * detection) already shares, so a listener sees the same fact regardless of which path caused it.
 * A record fits an event because an event is a value that transports data and must not change
 * after publication.
 *
 * @param sessionId identifier of the revoked session
 * @param accountHolderId owner the session belonged to
 * @param reason stable reason the session was revoked, such as {@code REUSE_DETECTED}
 * @param occurredAt the revoking command's decision instant
 */
public record SessionRevoked(UUID sessionId, UUID accountHolderId, String reason, Instant occurredAt) {
}
