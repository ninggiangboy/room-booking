package dev.ngb.backend.model;

/**
 * The source type of {@code case_evidence_items}.
 */
public enum CaseEvidenceSourceType {

    /** Participant upload. */
    PARTICIPANT_UPLOAD,

    /** Domain fact. */
    DOMAIN_FACT,

    /** Provider artifact. */
    PROVIDER_ARTIFACT,

    /** Agent capture. */
    AGENT_CAPTURE,

    /** Third party. */
    THIRD_PARTY,

    /** Derived. */
    DERIVED
}
