package dev.ngb.backend.model;

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
    REFRESH_TOKEN
}
