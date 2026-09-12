package dev.ngb.backend.model;

/**
 * What one relocation receipt is for.
 *
 * <p>Typed so that a case budget can be reasoned about afterwards: three hotel nights and thirty taxi
 * rides are different stories about the same total.</p>
 */
public enum RelocationExpenseType {
    /** A stay bought outside the platform. */
    ACCOMMODATION,
    /** Getting the guest there. */
    TRANSPORT,
    /** Food while waiting. */
    MEALS,
    /** Calls or data the guest had to buy. */
    COMMUNICATION,
    /** Luggage or belongings. */
    STORAGE,
    /** Anything the catalogue does not yet name. */
    OTHER
}
