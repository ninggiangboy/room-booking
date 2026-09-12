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
 * A specific room or apartment that a stay may be assigned to.
 *
 * <p>Optional by design. A guest booking a pooled hotel room type buys "a deluxe double", not room
 * 412, and the property may not decide which room that is until arrival. Modelling the unit
 * separately from the sellable category is what allows that assignment to happen late — or never,
 * for supply that has no meaningful unit identity.</p>
 *
 * <p>Units are retired rather than deleted so past assignments stay attributable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("physical_units")
public class PhysicalUnit {

    /** Primary key of the unit. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type this unit is one instance of. */
    private UUID accommodationTypeId;
    /** Identifier staff and guests use, such as a room number. */
    private String unitLabel;
    /** Floor the unit is on, where that is meaningful. */
    private @Nullable String floorLabel;
    /** Whether the unit can currently be assigned. */
    private PhysicalUnitStatus status;
    /** Why the unit is out of service; required while it is. */
    private @Nullable String outOfServiceReason;
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
     * Reports whether a stay may be assigned to this unit.
     *
     * @return {@code true} only while the unit is in service
     */
    public boolean isAssignable() {
        return status == PhysicalUnitStatus.AVAILABLE;
    }
}
