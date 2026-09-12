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
 * A landmark a guest measures distance to when judging where a property is.
 *
 * <p>Deliberately separate from the destination catalog. A {@code geo_areas} row of type
 * {@code POINT_OF_INTEREST} is somewhere a guest searches for; this is somewhere they measure from.
 * Keeping them apart means a beach can be worth showing a distance to without also becoming a
 * searchable destination with its own results page.</p>
 *
 * <p>The numeric coordinates are the only writable pair; PostgreSQL derives the PostGIS
 * {@code geography} point from them, so the numeric and spatial representations cannot drift apart.
 * That derived column is what makes distances come out in true metres rather than degrees, and it is
 * deliberately absent from this class because nothing may write it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("points_of_interest")
public class PointOfInterest {

    /** Primary key of the landmark. */
    @Id
    private @Nullable UUID id;
    /** Destination this landmark sits in, where it maps onto one. */
    private @Nullable UUID geoAreaId;
    /** Stable operator-facing key. */
    private String poiKey;
    /** Kind of landmark. */
    private PointOfInterestType poiType;
    /** Name as presented to a reader. */
    private String name;
    /** Normalized form used for fuzzy matching. */
    private String normalizedName;
    /** ISO 3166-1 alpha-2 country the landmark sits in. */
    private String countryCode;
    /** Latitude in degrees; half of the only writable coordinate pair. */
    private BigDecimal latitude;
    /** Longitude in degrees; half of the only writable coordinate pair. */
    private BigDecimal longitude;
    /** Relative prominence, used to choose which landmarks are worth showing. */
    private short importanceRank;
    /** Where the record came from. */
    private String source;
    /** Identifier the source assigned. */
    private @Nullable String sourceId;
    /** Version of the source data. */
    private @Nullable String sourceVersion;
    /** Whether the landmark is currently used. */
    private boolean active;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
