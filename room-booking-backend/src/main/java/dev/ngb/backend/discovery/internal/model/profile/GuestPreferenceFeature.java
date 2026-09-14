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
import org.springframework.data.relational.core.mapping.Table;

import dev.ngb.backend.discovery.internal.model.PreferenceDimensionKind;


/**
 * One preference dimension for one guest.
 *
 * <p>Importance, direction, and confidence are three separate fields on purpose. A guest who mentions
 * noise in every review may care enormously about quiet, or may have been unlucky once, and
 * collapsing those into one score loses the only distinction that matters. Long-term and recent
 * values are kept apart so recent intent can be expired without destroying stable history.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("guest_preference_features")
public class GuestPreferenceFeature {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The guest preference profile this row belongs to. */
    private UUID guestPreferenceProfileId;
    /** The kind of thing this preference is about. */
    private PreferenceDimensionKind dimensionKind;
    /** Which dimension within that kind. */
    private String dimensionKey;
    /** Trip shape this preference is held separately for, kept only where that segment has its own evidence. */
    private @Nullable TripContextSegment contextSegment;
    /** How much this dimension appears to affect the guest, as a fraction. Separate from direction. */
    private BigDecimal importance;
    /** Which way the preference points, or that it is not established. */
    private PreferenceDirection direction;
    /** The level the guest appears to prefer, where a direction is established. */
    private @Nullable BigDecimal preferredLevel;
    /** Lower bound of the preferred range, for a target-range preference. */
    private @Nullable BigDecimal targetLow;
    /** Upper bound of the preferred range. */
    private @Nullable BigDecimal targetHigh;
    /** How much this inference can be relied on, as a fraction. */
    private BigDecimal confidence;
    /** The value from stable history. */
    private @Nullable BigDecimal longTermValue;
    /** The value from recent sessions and trips, kept apart so it can be expired on its own. */
    private @Nullable BigDecimal recentValue;
    /** How many behavioural events stand behind this dimension. */
    private long evidenceEventCount;
    /** How many completed stays stand behind it. */
    private long evidenceStayCount;
    /** UTC instant of the most recent evidence. */
    private @Nullable Instant lastEvidenceAt;
    /** Whether the guest set or corrected this themselves, which is evidence of a different kind. */
    private boolean guestCorrected;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
