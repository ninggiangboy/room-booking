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
 * A defect in the building, and what was asked of the domains that own the consequences.
 *
 * <p>Inventory owns the calendar block and listing owns publication. This row records the request and
 * the answer, which is why the block is a foreign key rather than a status word.</p>
 *
 * <p>An unusable or unsafe condition cannot leave triage without a calendar decision having been
 * taken. Asking and being refused is a decision; silence is not.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("maintenance_records")
public class MaintenanceRecord {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Property affected. */
    private UUID propertyId;
    /** Listing affected, where it is listing-specific. */
    private @Nullable UUID listingId;
    /** Unit affected. */
    private @Nullable UUID physicalUnitId;
    /** Which asset or area. */
    private @Nullable String assetReference;
    /** Kind of defect. */
    private MaintenanceCategory category;
    /** How serious it is operationally. */
    private ExceptionSeverity severity;
    /** How much it affects somebody staying there. */
    private GuestImpact guestImpact;
    /** Where the record stands. */
    private MaintenanceState state;
    /** Who raised it. */
    private @Nullable UUID reportedByAccountHolderId;
    /** When it was raised. */
    private Instant reportedAt;
    /** Incident it came out of. */
    private @Nullable UUID sourceIncidentId;
    /** Task it came out of. */
    private @Nullable UUID sourceTaskId;
    /** What was observed. */
    private @Nullable String observationNote;
    /** When work is expected to start. */
    private @Nullable Instant expectedStartAt;
    /** When work is expected to finish. */
    private @Nullable Instant expectedEndAt;
    /** External work order. */
    private @Nullable String workOrderReference;
    /** When it was fixed. */
    private @Nullable Instant resolvedAt;
    /** What was done. */
    private @Nullable String resolutionNote;
    /** Evidence that it was done. */
    private @Nullable String resolutionEvidenceReference;
    /** Why it was postponed. */
    private @Nullable String deferralReason;
    /** What inventory said about an emergency block. */
    private BlockRequestState blockRequestState;
    /** When the block was asked for. */
    private @Nullable Instant blockRequestedAt;
    /** Block inventory created, where it did. */
    private @Nullable UUID inventoryBlockId;
    /** What was asked of listing publication. */
    private ListingActionRequest listingActionRequested;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this defect makes the property unfit to sell as it stands.
     *
     * @return true for unusable or unsafe conditions
     */
    public boolean barsOccupancy() {
        return guestImpact == GuestImpact.UNUSABLE || guestImpact == GuestImpact.UNSAFE;
    }

    /**
     * Whether the defect is still being dealt with.
     *
     * @return true while not resolved or cancelled
     */
    public boolean isOpen() {
        return state != MaintenanceState.RESOLVED && state != MaintenanceState.CANCELLED;
    }
}
