package dev.ngb.backend.hostops.internal.model.metric;

/**
 * What a host-facing metric can be computed about.
 */
public enum HostMetricSubjectKind {

    /** Listing. */
    LISTING,

    /** Accommodation type. */
    ACCOMMODATION_TYPE,

    /** Property. */
    PROPERTY,

    /** Host. */
    HOST
}
