package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The sellable category at a property: what a guest is actually buying a night of.
 *
 * <p>This — not the listing — is the inventory authority. {@link #inventoryMode} is the single
 * decision that shapes the whole booking path: a {@link InventoryMode#UNIQUE_RENTAL} is sold at most
 * once per night and defended by a range-overlap exclusion constraint, while a
 * {@link InventoryMode#QUANTITY_POOL} is sold down from a per-date count and defended by a capacity
 * check. The two use different database mechanisms, so the mode has to be declared on the supply
 * rather than inferred when someone tries to book.</p>
 *
 * <p>{@code ENTIRE_PLACE} may only be paired with exclusive sharing, enforced by the database: a
 * guest booking what they believe is a private home and finding strangers in the kitchen is not a
 * display problem.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("accommodation_types")
public class AccommodationType {

    /** Primary key of the accommodation type. */
    @Id
    private @Nullable UUID id;
    /** Property this category is sold at. */
    private UUID propertyId;
    /** Short human-readable reference used in operations and support. */
    private String referenceCode;
    /** Operator-facing name, such as "Deluxe double". */
    private String displayName;
    /** How availability is counted and defended against overselling. */
    private InventoryMode inventoryMode;
    /** How many can be sold per night; always one for a unique rental. */
    private int sellableQuantity;
    /** What the guest gets: the whole place, a private room, or a shared space. */
    private RoomType roomType;
    /** How much is shared with people outside the booking party. */
    private SpaceSharing spaceSharing;
    /** Occupancy the base price covers. */
    private short standardOccupancy;
    /** Largest party that may stay; never below the standard occupancy. */
    private short maximumOccupancy;
    /** Cap on adults, where one applies separately from total occupancy. */
    private @Nullable Short maximumAdults;
    /** Cap on children, where one applies separately. */
    private @Nullable Short maximumChildren;
    /** Whether infants may stay, typically without counting toward occupancy. */
    private boolean allowsInfants;
    /** Number of bedrooms. */
    private short bedroomCount;
    /** Number of beds. */
    private short bedCount;
    /** Number of bathrooms; fractional because a half-bath is a real thing. */
    private BigDecimal bathroomCount;
    /** Floor area in square metres, where the host has supplied it. */
    private @Nullable BigDecimal floorAreaSqm;
    /** Whether the category is in service. */
    private SupplyLifecycle lifecycleState;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this category is sold as one indivisible place.
     *
     * <p>Callers use this to choose which overselling defence applies, so it reads the declared mode
     * rather than inferring anything from the quantity.</p>
     *
     * @return {@code true} for a unique rental, {@code false} for a pooled type
     */
    public boolean isUniqueRental() {
        return inventoryMode == InventoryMode.UNIQUE_RENTAL;
    }

    /**
     * Reports whether a party of the given size can be accommodated.
     *
     * @param guestCount total guests who would occupy the space
     * @return {@code true} when the party fits within the maximum occupancy
     */
    public boolean accommodates(int guestCount) {
        return guestCount > 0 && guestCount <= maximumOccupancy;
    }
}
