package dev.ngb.backend.hostops.internal.model.benchmark;

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
import dev.ngb.backend.platform.GovernedRegistryStatus;
import dev.ngb.backend.market.internal.model.market.Market;
import dev.ngb.backend.supply.types.PropertyType;

import dev.ngb.backend.market.internal.model.market.Market;
import dev.ngb.backend.platform.GovernedRegistryStatus;
import dev.ngb.backend.supply.types.PropertyType;


/**
 * How a peer set is drawn, and the privacy floor below which nothing about it is published.
 * <p>Market intelligence is aggregate or it is a leak. A cohort names the minimum number of
 * distinct contributors and observations it will publish under, and the largest share one
 * contributor may supply before the aggregate is suppressed as too concentrated. Frozen once
 * published: lowering a floor after benchmarks were published under it would retroactively expose
 * the hosts who were in them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("benchmark_cohort_definitions")
public class BenchmarkCohortDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the cohort across its versions. */
    private String cohortKey;
    /** Which version of this cohort definition the row is. */
    private int cohortVersion;
    /** ISO 3166-1 alpha-2 market the cohort is drawn from. */
    private String marketCode;
    /** What the cohort is called on the host screen. */
    private String displayName;
    /**
     * How the peer set is drawn, in the words a host is shown; a benchmark against an unexplained
     * cohort cannot be argued with.
     */
    private String selectionDescription;
    /** The geographic area the cohort is confined to, where it is narrower than the market. */
    private @Nullable UUID geoAreaId;
    /** The property type the cohort is confined to, where it is confined to one. */
    private @Nullable PropertyType propertyType;
    /** Smallest guest capacity included in the cohort. */
    private @Nullable Short capacityBandLow;
    /** Largest guest capacity included in the cohort. */
    private @Nullable Short capacityBandHigh;
    /** Lowest nightly price included, in integer minor units. */
    private @Nullable Long priceBandLowMinor;
    /** Highest nightly price included, in integer minor units. */
    private @Nullable Long priceBandHighMinor;
    /** ISO 4217 alphabetic code the price band is denominated in. */
    private @Nullable String priceBandCurrency;
    /** How many distinct hosts must be in the cohort before any aggregate over it is published. */
    private int minimumContributors;
    /** How many observations must be in the cohort before any aggregate over it is published. */
    private int minimumObservations;
    /**
     * The largest share of a cohort one host may supply before the aggregate is suppressed as too
     * concentrated.
     */
    private BigDecimal maximumContributorShare;
    /** What is done when the cohort falls below its own floor. */
    private BenchmarkSuppressionRule suppressionRule;
    /** Where this cohort definition stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the cohort became available for benchmarking. */
    private @Nullable Instant publishedAt;
    /** UTC instant the cohort was withdrawn. */
    private @Nullable Instant retiredAt;
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
