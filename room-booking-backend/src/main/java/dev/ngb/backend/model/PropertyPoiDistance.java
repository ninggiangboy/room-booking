package dev.ngb.backend.model;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * How far a property is from a landmark, precomputed.
 *
 * <p>Precomputed because a listing page shows several of these, and computing them per request across
 * every candidate in a search result is the difference between a fast page and a slow one.</p>
 *
 * <p>{@link #travelTimeSeconds} and {@link #travelMode} are stored together and the database refuses
 * one without the other: "18 minutes" means nothing without saying by what. {@link #computedAt} and
 * {@link #catalogVersion} are what make a stale distance detectable once either endpoint has
 * moved.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("property_poi_distances")
public class PropertyPoiDistance {

    /** Composite property-and-landmark primary key. */
    @Id
    private PropertyPoiDistanceId id;
    /** Straight-line distance in metres. */
    private int straightLineMetres;
    /** Journey time in seconds, where one has been computed. */
    private @Nullable Integer travelTimeSeconds;
    /** How that journey time was measured; never omitted when a time is present. */
    private @Nullable TravelMode travelMode;
    /** UTC instant the distance was computed. */
    private Instant computedAt;
    /** Version of the geographic catalog it was computed against. */
    private @Nullable String catalogVersion;
}
