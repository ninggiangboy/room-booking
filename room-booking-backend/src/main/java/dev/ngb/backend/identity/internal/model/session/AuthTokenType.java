package dev.ngb.backend.identity.internal.model.session;

/**
 * Kinds of opaque token that share the {@code auth_tokens} table.
 *
 * <p>An enum limits values at compile time; a matching database check constraint protects values
 * written outside the Java application.</p>
 */
public enum AuthTokenType {
    /** One-time secret used to prove control of an email inbox. */
    EMAIL_VERIFICATION,
    /** One-time secret used to replace a forgotten password. */
    PASSWORD_RESET,
    /** Rotating session secret used to obtain a new access-token pair. */
    REFRESH_TOKEN,
    /** One-time numeric code used to prove control of a self-service contact channel. */
    CONTACT_CHANNEL_VERIFICATION,
    /** Short-lived proof that a stronger factor was just verified, presented before a sensitive
     * action. */
    STEP_UP
}
