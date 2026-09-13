package dev.ngb.backend.model;

/**
 * Which instant a feature's window is measured against.
 *
 * <p>Source-occurred is when the thing happened; source-available is when it became knowable. They
 * differ by the time a pipeline took, and training on the wrong one is a leak.</p>
 */
public enum FeatureEventTimeBasis {

    /** When the underlying event happened. */
    SOURCE_OCCURRED,

    /** When the underlying event became readable by a serving request. */
    SOURCE_AVAILABLE
}
