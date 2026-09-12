package dev.ngb.backend.model;

/**
 * Lifecycle shared by properties, accommodation types, and rate plans.
 *
 * <p>Supply is archived rather than deleted. A property that stops trading still has to explain the
 * bookings it accepted and the payouts it received, so the row stays and its state changes.</p>
 */
public enum SupplyLifecycle {
    /** Being set up; not sellable. */
    DRAFT,
    /** In service. */
    ACTIVE,
    /** Temporarily withdrawn from sale; existing obligations stand. */
    PAUSED,
    /** Permanently withdrawn; retained for history. */
    ARCHIVED
}
