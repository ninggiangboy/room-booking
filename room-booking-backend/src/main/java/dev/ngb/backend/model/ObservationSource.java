package dev.ngb.backend.model;

/**
 * Where a piece of provider evidence came from.
 *
 * <p>The source decides how the evidence is deduplicated and how far it is trusted. A webhook is
 * evidence only once its signature verified.</p>
 */
public enum ObservationSource {
    /** The provider's direct answer to a submitted request. */
    API_RESPONSE,
    /** A signature-verified event the provider pushed. */
    WEBHOOK,
    /** An authenticated server-to-server retrieval of current state. */
    QUERY,
    /** A row from a restricted provider file or export. */
    RECONCILIATION_IMPORT
}
