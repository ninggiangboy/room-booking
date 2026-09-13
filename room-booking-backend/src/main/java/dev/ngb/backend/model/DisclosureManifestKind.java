package dev.ngb.backend.model;

/**
 * The manifest kind of {@code evidence_disclosure_manifests}.
 */
public enum DisclosureManifestKind {

    /** Provider dispute. */
    PROVIDER_DISPUTE,

    /** Protection claim. */
    PROTECTION_CLAIM,

    /** Participant disclosure. */
    PARTICIPANT_DISCLOSURE,

    /** Regulator request. */
    REGULATOR_REQUEST,

    /** Legal request. */
    LEGAL_REQUEST,

    /** Data subject request. */
    DATA_SUBJECT_REQUEST
}
