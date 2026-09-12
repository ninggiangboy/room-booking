package dev.ngb.backend.model;

/**
 * What happened to one relocation offer.
 *
 * <p>Declined offers are kept. Three declines is evidence about the offers, not about the guest, and a
 * case that settled on the fourth option needs the first three to stay explainable.</p>
 */
public enum RelocationOfferState {
    /** Presented to the guest. */
    OFFERED,
    /** Taken. At most one per case. */
    ACCEPTED,
    /** Refused, with a reason. */
    DECLINED,
    /** Not answered before it lapsed. */
    EXPIRED,
    /** Pulled before the guest answered, usually because the supply went. */
    WITHDRAWN
}
