package dev.ngb.backend.model;

/**
 * The provenance ladder, from an authoritative domain fact down to an unverified external assertion.
 *
 * <p>A controlled ladder rather than one confidence number, because quality depends on the question: a
 * payment record can prove movement but not the condition of a sofa.</p>
 */
public enum EvidenceProvenanceClass {

    /** An immutable fact owned by another Room Booking domain. */
    AUTHORITATIVE_DOMAIN_FACT,

    /** A provider artifact whose origin was verified. */
    AUTHENTICATED_PROVIDER_FACT,

    /** A participant artifact with capture metadata placing it at the time in question. */
    CONTEMPORANEOUS_PARTICIPANT_ARTIFACT,

    /** An artifact or statement supplied after the fact. */
    LATER_PARTICIPANT_ASSERTION,

    /** Content produced from other evidence, with its transformation recorded. */
    DERIVED_WITH_LINEAGE,

    /** A claim from outside the platform that nothing corroborates. */
    UNVERIFIED_EXTERNAL_ASSERTION
}
