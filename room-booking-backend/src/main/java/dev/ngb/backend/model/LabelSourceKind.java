package dev.ngb.backend.model;

/**
 * Where the outcome behind a label came from.
 *
 * <p>A rule or model that caused the review may not train on its own decision as ground truth, and
 * an unverified report is not ground truth either. Both exclusions are check constraints on the
 * label rather than conventions in a training pipeline.</p>
 */
public enum LabelSourceKind {
    /** A person investigated and concluded. */
    HUMAN_ADJUDICATION,
    /** An issuer, provider or authority outcome. */
    PROVIDER_OUTCOME,
    /** A committed fact from an authoritative domain. */
    DOMAIN_EVENT,
    /** The platform's own decision; never training ground truth. */
    AUTOMATED_DECISION,
    /** Somebody's allegation; never training ground truth. */
    USER_REPORT;
}
