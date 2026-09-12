package dev.ngb.backend.model;

/**
 * How an accommodation type's availability is counted and defended against overselling.
 *
 * <p>This is the single decision that shapes the whole inventory and booking path. A
 * {@link #UNIQUE_RENTAL} is one indivisible thing: it is sold once per night and protected by a
 * range-overlap exclusion constraint. A {@link #QUANTITY_POOL} is a count of interchangeable rooms:
 * it is sold down from a per-date quantity and protected by a capacity check.</p>
 *
 * <p>Because the two are defended by different database mechanisms, the mode cannot be inferred at
 * booking time — it has to be declared on the supply, which is why it lives here and not on the
 * calendar.</p>
 */
public enum InventoryMode {
    /** One indivisible place, sold at most once per night. */
    UNIQUE_RENTAL,
    /** Interchangeable rooms sold from a per-date count. */
    QUANTITY_POOL
}
