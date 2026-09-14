package dev.ngb.backend.inventory.internal.model.resource;

/**
 * Whether an inventory resource may be sold from.
 *
 * <p>Retired resources are kept because their calendar explains stays that already happened.</p>
 */
public enum InventoryResourceStatus {
    /** Sellable. */
    ACTIVE,
    /** Temporarily not sellable; existing claims stand. */
    SUSPENDED,
    /** Permanently withdrawn; retained for history. */
    RETIRED
}
