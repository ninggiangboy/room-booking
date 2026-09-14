package dev.ngb.backend.trust.internal.model.signal;

/**
 * How much weight an observation may be given.
 *
 * <p>Distinct from a numeric confidence, which is optional. The class is what policy reasons about,
 * and {@code UNKNOWN} is explicitly not {@code VERIFIED} at a low number.</p>
 */
public enum SignalConfidenceClass {
    /** Checked by this platform. */
    VERIFIED,
    /** From a provider whose response was authenticated. */
    AUTHENTICATED,
    /** Independently supported by another observation. */
    CORROBORATED,
    /** Deterministically derived from verified inputs. */
    DERIVED,
    /** A calibrated model estimate. */
    CALIBRATED,
    /** Asserted but not checked. */
    UNVERIFIED,
    /** Not established either way. */
    UNKNOWN;
}
