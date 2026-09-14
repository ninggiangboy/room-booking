package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * What evidence supports an exposure.
 *
 * <p>Server assignment alone is not viewability. Where a surface defines exposure by rendering, the
 * client has to have reported it.</p>
 */
public enum ExposureEvidenceSource {

    /** The server knows the treatment could have affected the experience. */
    SERVER,

    /** A client reported the rendering that the exposure rule requires. */
    CLIENT_CONFIRMED
}
