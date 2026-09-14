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

import dev.ngb.backend.review.types.DerivedProfileStatus;


/**
 * What a listing with no evidence is assumed to be, and at which geographic level that came from.
 *
 * <p>The fallback level is stored because &quot;we used the country prior&quot; and &quot;this
 * neighbourhood is like this&quot; are different statements, and only one of them is honest about a
 * new destination. A prior thin enough to describe a handful of listings may be computed but not
 * served.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("discovery_market_priors")
public class DiscoveryMarketPrior {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The geographic level this prior was computed at. */
    private PriorScopeLevel scopeLevel;
    /** The geo area this row belongs to. */
    private @Nullable UUID geoAreaId;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Which metric the prior is for. */
    private String metricKey;
    /** The value an unevidenced subject is shrunk towards. */
    private BigDecimal priorMean;
    /** How much evidence the prior is worth, in the same units as effective evidence. */
    private BigDecimal priorStrength;
    /** How many observations the prior was computed from. */
    private long sampleSize;
    /** Observations required before this prior may be served, so a thin one cannot leak the few listings behind it. */
    private int minimumSampleSize;
    /** Which version of the aggregation rules produced this row. */
    private String aggregationVersion;
    /** UTC instant up to which inputs were included, so a rebuild is reproducible. */
    private @Nullable Instant inputWatermark;
    /** Whether this prior is the one being served. */
    private DerivedProfileStatus status;
    /** UTC instant the prior was computed. */
    private Instant computedAt;
    /** UTC instant after which the prior is stale and must be recomputed. */
    private Instant expiresAt;
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
