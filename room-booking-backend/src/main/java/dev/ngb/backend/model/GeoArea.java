package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * A place a guest can name as their destination.
 *
 * <p>This is the catalog every other domain points at when it needs to say <em>where</em>. A
 * property sits in one, a search is for one, a demand forecast and a market benchmark are computed
 * over one, and a saved search or waitlist entry watches one. Migration {@code 010} created it and
 * migration {@code 017} deliberately kept it rather than rebuilding it, because the model was
 * already right.</p>
 *
 * <p>The catalog is imported from an external source rather than authored here, which is why
 * {@code source}, {@code sourceId} and {@code sourceVersion} are not optional bookkeeping: they are
 * how a row is matched to its upstream record when the source is refreshed, and how a disagreement
 * between the platform and a map provider is traced. It is also why the timestamps keep the database
 * defaults migration {@code 011} removed elsewhere -- no application workflow writes this table, so
 * there is no application clock that owns them.</p>
 *
 * <p>The PostGIS {@code center} and {@code boundary} columns are deliberately absent. They are
 * written by the import pipeline in SQL and read through spatial predicates such as
 * {@code ST_DWithin} and {@code ST_Contains}, which a mapped field cannot express; exposing them as
 * opaque values would invite a caller to compare them in Java, where the answer would come out in
 * degrees rather than metres.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("geo_areas")
public class GeoArea {

    /** Primary key of the destination. */
    @Id
    private @Nullable UUID id;
    /** Destination that contains this one; null only for a country. */
    private @Nullable UUID parentId;
    /** ISO 3166-1 alpha-2 country the destination belongs to. */
    private String countryCode;
    /** Which kind of place this row describes. */
    private GeoAreaType areaType;
    /** How deep in its country's administrative hierarchy the area sits, where that applies. */
    private @Nullable Short adminLevel;
    /** Canonical name, as presented to a reader. */
    private String name;
    /** Case-folded, accent-stripped form the typo-tolerant name search matches against. */
    private String normalizedName;
    /** Which external catalog the row was imported from. */
    private String source;
    /** Identifier that source assigned, unique within it. */
    private String sourceId;
    /** Which release of the source data the row was last reconciled against. */
    private @Nullable String sourceVersion;
    /** Whether the destination may still be offered; a superseded place is deactivated, not deleted. */
    private boolean active;
    /** Civil date the place began to exist under this definition, where the source records one. */
    private @Nullable LocalDate validFrom;
    /** Civil date the definition stopped applying, where the source records one. */
    private @Nullable LocalDate validUntil;
    /** UTC instant the row was created. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private long version;
}
