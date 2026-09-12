package dev.ngb.backend.model;

/**
 * How strongly the person taking a privileged finance action was authenticated.
 *
 * <p>Configured high-risk actions require step-up authentication. Recording the strength means an
 * after-the-fact review can tell an ordinary session apart from a deliberate re-authentication.</p>
 */
public enum AuthenticationStrength {
    /** An ordinary authenticated session. */
    SESSION,
    /** Re-authenticated specifically for this action. */
    STEP_UP,
    /** Confirmed with a hardware authenticator. */
    HARDWARE_TOKEN
}
