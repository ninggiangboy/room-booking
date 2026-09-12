package dev.ngb.backend.model;

/**
 * Whether a specific room or apartment can currently be assigned to a stay.
 *
 * <p>{@link #OUT_OF_SERVICE} is temporary and must carry a reason, so a room withdrawn for
 * maintenance is distinguishable from one withdrawn after an incident. {@link #RETIRED} is permanent
 * and the row is kept so past assignments stay attributable.</p>
 */
public enum PhysicalUnitStatus {
    /** Assignable to a stay. */
    AVAILABLE,
    /** Temporarily unassignable, with a recorded reason. */
    OUT_OF_SERVICE,
    /** Permanently withdrawn; retained for history. */
    RETIRED
}
