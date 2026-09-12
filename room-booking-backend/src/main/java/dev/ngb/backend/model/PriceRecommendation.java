package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * What the model proposed for one night, and the inputs that produced it.
 *
 * <p>Kept apart from the price actually charged because a recommendation is advice: the host may take
 * it, ignore it, or override it. Evaluating the optimizer later requires knowing which of those
 * happened — a recommendation nobody saw and one a host deliberately rejected say very different
 * things about the model.</p>
 *
 * <p>Every recommendation expires. Advice with no shelf life is advice about a demand picture that no
 * longer exists.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("price_recommendations")
public class PriceRecommendation {

    /** Primary key of the recommendation. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type the advice concerns. */
    private UUID accommodationTypeId;
    /** The night it concerns, in the property's local calendar. */
    private LocalDate stayDate;
    /** ISO 4217 currency of both amounts. */
    private String currency;
    /** Price in effect when the advice was generated, in minor units. */
    private @Nullable Long currentAmountMinor;
    /** Price proposed, in minor units. */
    private long recommendedAmountMinor;
    /** Modelled chance the night sells at the proposed price, between zero and one. */
    private @Nullable BigDecimal predictedBookingProbability;
    /** Modelled occupancy for the surrounding period, as a percentage. */
    private @Nullable BigDecimal predictedOccupancyPercent;
    /** How much evidence stands behind the proposal. */
    private @Nullable RecommendationConfidence confidence;
    /** Which limit stopped the optimizer going further, such as the host's floor. */
    private @Nullable String bindingConstraint;
    /** Stable codes explaining the proposal, as an immutable JSON array. */
    private @Nullable JsonDocument reasonCodes;
    /** Version of the feature set the model read. */
    private @Nullable String featureSetVersion;
    /** Version of the model that produced the prediction. */
    private @Nullable String modelVersion;
    /** Version of the optimizer that turned the prediction into a price. */
    private @Nullable String optimizerVersion;
    /** What became of the advice. */
    private RecommendationDecisionState decisionState;
    /** UTC instant somebody decided; paired with a non-pending state. */
    private @Nullable Instant decidedAt;
    /** UTC instant the advice was produced. */
    private Instant generatedAt;
    /** UTC instant after which it should no longer be acted on. */
    private Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Reports whether the advice has gone stale at an instant.
     *
     * <p>Expiry is a fact about time, not a write: a lapsed recommendation may still be marked
     * {@link RecommendationDecisionState#PENDING} until a sweep transitions it.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the instant is at or past the expiry
     */
    public boolean hasLapsedAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }
}
