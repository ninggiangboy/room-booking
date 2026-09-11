package dev.ngb.backend.model;

/**
 * Which authentication-related action was attempted.
 *
 * <p>Attempts are recorded whatever their outcome, because velocity control and account-takeover
 * investigation depend on failures at least as much as on successes.</p>
 */
public enum AuthAttemptType {
    /** An attempt to establish a session. */
    LOGIN,
    /** An attempt to exchange a refresh token. */
    REFRESH,
    /** An attempt to begin or complete a password reset. */
    PASSWORD_RESET,
    /** An attempt to verify an email address. */
    EMAIL_VERIFICATION,
    /** An attempt to satisfy a second factor. */
    MFA_CHALLENGE,
    /** An attempt to raise assurance for a sensitive command. */
    STEP_UP
}
