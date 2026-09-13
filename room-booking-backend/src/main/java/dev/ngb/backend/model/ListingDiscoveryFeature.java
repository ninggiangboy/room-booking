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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One feature within one listing profile version, with its own confidence and evidence beside it.
 *
 * <p>An unknown feature is absent or explicitly insufficient; it is never stored as a zero, because
 * zero quality and no information are different claims and only one of them should be able to sink a
 * listing.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_discovery_features")
public class ListingDiscoveryFeature {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The listing discovery profile this row belongs to. */
    private UUID listingDiscoveryProfileId;
    /** Which feature this row carries. */
    private String featureKey;
    /** Which family of listing signal the feature belongs to. */
    private DiscoveryFeatureFamily featureFamily;
    /** The numeric value, where the feature has one. */
    private @Nullable BigDecimal numericValue;
    /** The categorical value, where the feature has one instead. */
    private @Nullable String textValue;
    /** How much the value can be relied on, as a fraction. */
    private @Nullable BigDecimal confidence;
    /** Evidence weight behind the value after decay and reliability weighting. */
    private @Nullable BigDecimal effectiveEvidence;
    /** Which evidence class this row carries. */
    private DiscoveryEvidenceClass evidenceClass;
    /** Signed change between the recent window and the all-time aggregate. */
    private @Nullable BigDecimal trend;
    /** UTC instant of the most recent evidence behind the value. */
    private @Nullable Instant lastEvidenceAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
