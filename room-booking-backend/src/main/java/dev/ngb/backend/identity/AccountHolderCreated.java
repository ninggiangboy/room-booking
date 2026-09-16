package dev.ngb.backend.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event marking that a new account holder now exists.
 *
 * <p>Unlike {@link EmailVerificationIssued} and {@link PasswordResetIssued}, this carries no
 * secret, so it is published through Spring Modulith's durable
 * {@code @ApplicationModuleListener} registry rather than a bare listener — see
 * {@code docs/architecture/event-publication-registry.md}. A record fits an event because an
 * event is a value that transports data and must not change after publication.
 *
 * @param accountHolderId identifier of the newly created holder
 * @param occurredAt the registration command's decision instant
 */
public record AccountHolderCreated(UUID accountHolderId, Instant occurredAt) {
}
