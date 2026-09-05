package dev.ngb.backend.event;

/**
 * Announces a committed password-reset token that must be delivered to its owner.
 *
 * <p>The record is immutable because a published event represents a fact. A transactional event
 * listener receives it only after the token database transaction commits.</p>
 *
 * @param recipient normalized account email
 * @param rawToken one-time secret that is never stored in raw form
 */
public record PasswordResetIssued(String recipient, String rawToken) {
}
