package dev.ngb.backend.model;

/**
 * What kind of thing an observation is.
 *
 * <p>The evidence quality ladder as a column: policy reads it to decide what an observation is
 * allowed to justify. A single party's allegation may never carry a verified confidence class,
 * which is a check constraint rather than a convention.</p>
 */
public enum SignalProvenance {
    /** A fact recorded by an authoritative domain of this platform. */
    DOMAIN_FACT,
    /** An authenticated observation from an external provider. */
    PROVIDER_OBSERVATION,
    /** Independent observations that agree. */
    CORROBORATED_OBSERVATION,
    /** A deterministic derivation from other signals. */
    DERIVED_SIGNAL,
    /** A calibrated model inference. */
    MODEL_INFERENCE,
    /** One party's unverified account of something. */
    USER_ALLEGATION;
}
