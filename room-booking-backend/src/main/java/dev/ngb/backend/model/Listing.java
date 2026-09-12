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
 * The public presentation of an accommodation type.
 *
 * <p><strong>A listing is not inventory authority.</strong> It decides what a guest sees and whether
 * the category is offered for sale at all; what can actually be sold on a given night is decided by
 * the accommodation type's inventory in migration {@code 018}. Search may show a listing, but only
 * inventory may promise a night.</p>
 *
 * <p>At most one listing per accommodation type may sit in a live state, because two published
 * listings for the same sellable inventory would compete for the same nights and double-count in
 * search results.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listings")
public class Listing {

    /** Primary key of the listing. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type this listing presents. */
    private UUID accommodationTypeId;
    /** Short human-readable reference used in operations and support. */
    private String referenceCode;
    /** URL-facing identifier, unique where present. */
    private @Nullable String slug;
    /** Whether the listing is visible and offered for sale. */
    private ListingStatus status;
    /** UTC instant the listing first became publicly bookable. */
    private @Nullable Instant publishedAt;
    /** UTC instant it was most recently withdrawn from sale. */
    private @Nullable Instant unpublishedAt;
    /** Content-quality score out of 100, where one has been computed. */
    private @Nullable BigDecimal qualityScore;
    /** UTC instant that score was computed; paired with {@link #qualityScore}. */
    private @Nullable Instant qualityScoredAt;
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
     * Reports whether the listing is currently offered to guests.
     *
     * <p>This is a statement about visibility, never about availability: a published listing with no
     * sellable inventory for the requested nights is a normal and correct state.</p>
     *
     * @return {@code true} when the listing is published
     */
    public boolean isPubliclyOffered() {
        return status == ListingStatus.PUBLISHED;
    }
}
