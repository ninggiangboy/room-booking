package dev.ngb.backend.model;

/**
 * Which overselling defence applies to an inventory resource.
 *
 * <p>Not a description — a dispatch. {@link #SINGLE_UNIT} and {@link #PHYSICAL_UNIT} are protected by
 * a range-overlap exclusion constraint, {@link #QUANTITY_POOL} by a per-date capacity check. The two
 * mechanisms are not interchangeable: an exclusion constraint cannot express "at most three at once",
 * and a counter cannot express "these exact nights are taken".</p>
 */
public enum InventoryResourceType {
    /** One indivisible place with no separately identified room. */
    SINGLE_UNIT,
    /** The calendar of one specific, separately identified room. */
    PHYSICAL_UNIT,
    /** A count of interchangeable rooms sold from a per-date quantity. */
    QUANTITY_POOL
}
