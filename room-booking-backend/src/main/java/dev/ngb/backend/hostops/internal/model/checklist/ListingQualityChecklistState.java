package dev.ngb.backend.hostops.internal.model.checklist;

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
 * Where one listing stands on one checklist item.
 * <p>Satisfied means the platform looked again and the thing it complained about is gone, not that
 * the host pressed a button. Reopening a satisfied item needs a fresh observation rather than the
 * one that was already resolved.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_quality_checklist_states")
public class ListingQualityChecklistState {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The listing being measured. */
    private UUID listingId;
    /** The checklist item this row is the listings standing against. */
    private UUID checklistItemId;
    /** Where the listing stands on this item. */
    private ChecklistItemState itemState;
    /** UTC instant the item was most recently found to be outstanding. */
    private Instant detectedAt;
    /** UTC instant the platform last looked. */
    private Instant lastEvaluatedAt;
    /**
     * Reference to what the platform looked at, held in its owning system rather than copied here.
     */
    private @Nullable String evidenceReference;
    /** What the platform found, in the words the host is shown. */
    private @Nullable String evidenceDetail;
    /** The listing quality profile that raised the item, where one did. */
    private @Nullable UUID qualityProfileId;
    /** UTC instant the hosts change was made. */
    private @Nullable Instant satisfiedAt;
    /** UTC instant the platform looked again and found the item resolved. */
    private @Nullable Instant verifiedAt;
    /** UTC instant the host set the item aside. */
    private @Nullable Instant dismissedAt;
    /** The person who set the item aside. */
    private @Nullable UUID dismissedBy;
    /** Approved reason code recording why the item was set aside. */
    private @Nullable String dismissalReason;
    /** Approved reason code recording why the item does not apply to this listing. */
    private @Nullable String notApplicableReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
