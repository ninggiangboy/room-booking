package dev.ngb.backend.hostops.internal.model.metric;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.supply.internal.model.property.Property;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.supply.internal.model.property.Property;

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
