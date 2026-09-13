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
 * The budget that bounded exposure for new or uncertain listings is spent from.
 *
 * <p>Exploration spends guest attention. That is worth doing and it is not free, so it gets a window,
 * a traffic ceiling, a per-listing cap, and a quality floor, and a window can never record consuming
 * more than it allocated.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("exploration_budget_windows")
public class ExplorationBudgetWindow {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming this budget window. */
    private String windowKey;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The geo area this row belongs to. */
    private @Nullable UUID geoAreaId;
    /** The ranking policy version this row belongs to. */
    private UUID rankingPolicyVersionId;
    /** Where the window stands. */
    private ExplorationWindowState state;
    /** UTC instant the window opens. */
    private Instant opensAt;
    /** UTC instant it closes. */
    private Instant closesAt;
    /** Share of impressions exploration may take within the window. */
    private BigDecimal trafficShareLimit;
    /** Most exploration impressions the window may fund. */
    private long impressionLimit;
    /** How many it has funded, which can never exceed the limit. */
    private long impressionsConsumed;
    /** Most exploration impressions any one listing may take from the window. */
    private int perListingImpressionCap;
    /** Quality a listing must reach before the window will fund it. */
    private BigDecimal qualityFloor;
    /** Whether trust and safety eligibility is checked before funding an impression. */
    private boolean safetyEligibilityRequired;
    /** Reference to the experiment this exploration is logged under. */
    private @Nullable String experimentReference;
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
