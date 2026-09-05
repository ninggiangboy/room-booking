package dev.ngb.backend.event;

/**
 * Immutable event carrying a newly issued raw token to the post-commit email listener.
 *
 * <p>A record fits an event because an event is a value that transports data and must not change
 * after publication.</p>
 *
 * @param recipient normalized destination email address
 * @param rawToken secret placed in the verification link but never stored in raw form
 */
public record EmailVerificationIssued(String recipient, String rawToken) {
}
