package dev.ngb.backend.discovery.internal.model.profile;

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
import dev.ngb.backend.review.types.DerivedProfileStatus;

import dev.ngb.backend.discovery.internal.model.PriorFallbackLevel;
import dev.ngb.backend.review.types.DerivedProfileStatus;


/**
 * The versioned, rebuildable read model discovery serves one listing from.
 *
 * <p>It deliberately holds no availability and no trip price: those change by date and are
 * request-time facts owned by inventory and pricing. What it holds is comparison and summary, each
 * dated, each expiring, each naming the evidence version behind it. A listing with no completed
 * stays may only carry a rating if it also names the prior that rating came from.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_discovery_profiles")
public class ListingDiscoveryProfile {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The listing this row belongs to. */
    private UUID listingId;
    /** Which version of the profile applies. */
    private int profileVersion;
    /** Whether this profile version is the one being served. */
    private DerivedProfileStatus status;
    /** The aspect profile version this row belongs to. */
    private @Nullable UUID aspectProfileVersionId;
    /** The listing quality profile this row belongs to. */
    private @Nullable UUID listingQualityProfileId;
    /** Shrunk overall rating on the one-to-five scale, never a raw average. */
    private @Nullable BigDecimal smoothedOverallRating;
    /** How many completed stay there are. */
    private long completedStayCount;
    /** How many review there are. */
    private long reviewCount;
    /** Share of bookings the host cancelled, as a fraction. */
    private @Nullable BigDecimal hostCancellationRate;
    /** How dependably the listing delivers what it promises, as a fraction. */
    private @Nullable BigDecimal reliabilityScore;
    /** Where the trip total sits among comparable supply, as a fraction. Never a raw amount. */
    private @Nullable BigDecimal relativePricePercentile;
    /** How much of the total is visible up front, as a fraction. */
    private @Nullable BigDecimal feeTransparency;
    /** How complete the listing content and media are, as a fraction. */
    private @Nullable BigDecimal contentCompleteness;
    /** Days since the listing was first published. */
    private @Nullable Integer listingAgeDays;
    /** UTC instant last meaningful update. */
    private @Nullable Instant lastMeaningfulUpdateAt;
    /** Which prior this profile fell back on, or none if it had evidence of its own. */
    private PriorFallbackLevel priorFallbackLevel;
    /** The prior scope geo area this row belongs to. */
    private @Nullable UUID priorScopeGeoAreaId;
    /** Digest of the feature schema, so it can be shown later to be unchanged. */
    private String featureSchemaDigest;
    /** Which version of the aggregation rules produced this row. */
    private String aggregationVersion;
    /** UTC instant up to which inputs were included. */
    private @Nullable Instant inputWatermark;
    /** Digest of the source manifest, so it can be shown later to be unchanged. */
    private @Nullable String sourceManifestDigest;
    /** UTC instant the profile was computed. */
    private Instant computedAt;
    /** UTC instant after which the profile is stale. */
    private Instant expiresAt;
    /** The superseded by profile this row belongs to. */
    private @Nullable UUID supersededByProfileId;
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
