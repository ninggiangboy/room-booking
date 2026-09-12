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
 * What the platform has learned about how useful a destination is.
 *
 * <p>Kept beside the geographic catalog rather than inside it, so that reimporting the catalog does
 * not discard what has been learned about which destinations people actually search for. The area is
 * the primary key, so a destination has exactly one profile.</p>
 *
 * <p>{@link #bookablePropertyCount} guards autocomplete: suggesting a destination with nothing
 * bookable in it wastes the guest's only query.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("geo_area_search_profiles")
public class GeoAreaSearchProfile {

    /** Geographic area this profile describes; also the primary key. */
    @Id
    private UUID geoAreaId;
    /** Relative search popularity, higher meaning more sought after. */
    private int searchVolumeRank;
    /** How many bookable properties the area currently contains. */
    private int bookablePropertyCount;
    /** Whether the area may be offered in destination autocomplete at all. */
    private boolean isSuggestable;
    /** UTC instant the derived figures were last recomputed. */
    private Instant computedAt;
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
     * Reports whether this destination is worth offering in autocomplete.
     *
     * @return {@code true} when the area is suggestable and has something bookable in it
     */
    public boolean isWorthSuggesting() {
        return isSuggestable && bookablePropertyCount > 0;
    }
}
