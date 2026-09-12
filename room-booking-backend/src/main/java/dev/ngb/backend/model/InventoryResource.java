package dev.ngb.backend.model;

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
 * The stable calendar identity that nights are actually sold from.
 *
 * <p>{@link #resourceType} is a dispatch rather than a description: it decides which database
 * mechanism defends this resource against overselling. Single and physical units are protected by a
 * range-overlap exclusion constraint on {@code inventory_claims}; a quantity pool is protected by the
 * per-date capacity check on {@code availability_days}. The two are not interchangeable, which is why
 * the type is declared on the supply and copied onto every claim.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("inventory_resources")
public class InventoryResource {

    /** Primary key of the resource. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type whose nights this resource sells. */
    private UUID accommodationTypeId;
    /** Which overselling defence applies. */
    private InventoryResourceType resourceType;
    /** Specific room this calendar belongs to; present only for a physical-unit resource. */
    private @Nullable UUID physicalUnitId;
    /** How many can be sold per night; always one except for a pool. */
    private int sellableQuantity;
    /** Whether the resource may be sold from. */
    private InventoryResourceStatus status;
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
     * Reports whether this resource is defended by range-overlap rather than by capacity.
     *
     * <p>Callers use this to choose which preflight to run before writing a claim. It reads the
     * declared type rather than inferring anything from the quantity, because the quantity is a
     * consequence of the type and not the other way round.</p>
     *
     * @return {@code true} when overlapping claims are impossible on this resource
     */
    public boolean isExclusive() {
        return resourceType != InventoryResourceType.QUANTITY_POOL;
    }
}
