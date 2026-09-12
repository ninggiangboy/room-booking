package dev.ngb.backend.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier pairing an inventory resource with one civil date.
 *
 * <p>The date is a civil date in the property's own time zone, not an instant: "the night of the 3rd"
 * is a fact about the property's calendar rather than about a moment in UTC.</p>
 *
 * <p>{@code @EqualsAndHashCode} gives the key value semantics, and {@link Serializable} lets
 * persistence infrastructure transport the compound key as one value.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AvailabilityDayId implements Serializable {

    /** Inventory resource side of the compound key. */
    private UUID inventoryResourceId;
    /** Civil stay date side of the compound key. */
    private LocalDate stayDate;
}
