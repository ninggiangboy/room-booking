package dev.ngb.backend.trust.internal.model.feature;

/**
 * Which clock a feature window is measured against.
 *
 * <p>Event time and ingestion time differ whenever evidence arrives late, and a point-in-time
 * replay has to know which one the original computation used.</p>
 */
public enum FeatureEventTimeRule {
    /** When the underlying thing happened. */
    EVENT_TIME,
    /** When the platform learned of it. */
    INGESTION_TIME,
    /** The instant the evaluation was made. */
    DECISION_TIME
}
