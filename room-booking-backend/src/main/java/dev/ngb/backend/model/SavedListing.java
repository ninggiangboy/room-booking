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
 * One guest's saved listing, kept as state rather than as a row that disappears.
 *
 * <p>An explicit save is the strongest cheap preference signal there is. Unsaving is a state change,
 * not a delete: a guest who saves, unsaves, and saves again has said something a vanished row
 * cannot.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("saved_listings")
public class SavedListing {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The account holder this row belongs to. */
    private UUID accountHolderId;
    /** The listing this row belongs to. */
    private UUID listingId;
    /** Whether the listing is currently saved. */
    private SavedListingState state;
    /** UTC instant of the most recent save. */
    private Instant savedAt;
    /** UTC instant it was unsaved; set exactly when the state is unsaved. */
    private @Nullable Instant unsavedAt;
    /** How many times it has been saved, which a guest repeating the act is telling us something by. */
    private int saveCount;
    /** Where the guest was when they saved it. */
    private SavedListingSurface sourceSurface;
    /** Short private note the guest attached. */
    private @Nullable String note;
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
