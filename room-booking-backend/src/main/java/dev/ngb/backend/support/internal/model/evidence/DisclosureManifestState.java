package dev.ngb.backend.support.internal.model.evidence;

/**
 * The state of {@code evidence_disclosure_manifests}.
 */
public enum DisclosureManifestState {

    /** Draft. */
    DRAFT,

    /** Frozen. */
    FROZEN,

    /** Approved. */
    APPROVED,

    /** Disclosed. */
    DISCLOSED,

    /** Expired. */
    EXPIRED,

    /** Revoked. */
    REVOKED
}
