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
 * The frozen terms of a promotion, including who pays for it.
 *
 * <p>A redemption cites a version, not a campaign. That is what makes the funding split binding: the
 * share a host agreed to fund is the share recorded on the version the guest actually redeemed, not
 * whatever the campaign says by the time the booking settles.</p>
 *
 * <p>Each benefit shape carries exactly the parameters it needs and the database refuses the rest —
 * a percentage benefit that also names a fixed amount has no defined order of application, so two
 * evaluations of it could legitimately disagree.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("promotion_versions")
public class PromotionVersion {

    /** Primary key of the version. */
    @Id
    private @Nullable UUID id;
    /** Promotion this is a version of. */
    private UUID promotionId;
    /** Monotonic number within the promotion, starting at one. */
    private int versionNumber;
    /** Who qualifies, as an immutable JSON condition. */
    private JsonDocument eligibilityPayload;
    /** The shape of what is given away. */
    private PromotionBenefitType benefitType;
    /** Percentage taken off, for a percentage benefit. */
    private @Nullable BigDecimal benefitPercent;
    /** Fixed amount taken off in minor units, for a fixed-amount benefit. */
    private @Nullable Long benefitAmountMinor;
    /** ISO 4217 currency of that amount; present exactly when it is. */
    private @Nullable String benefitCurrency;
    /** Cap on the benefit in minor units, which bounds a percentage on an expensive stay. */
    private @Nullable Long maximumBenefitMinor;
    /** Share of the benefit the host bears, as a percentage; the platform bears the rest. */
    private BigDecimal hostFundedPercent;
    /** Rivals sharing a group; only one of them may apply. */
    private @Nullable String stackingGroup;
    /** Whether this benefit may combine with others at all. */
    private boolean isStackable;
    /** Whether the discount lowers the taxable base or is returned after tax. */
    private PromotionTaxTreatment taxTreatment;
    /** UTC instant bookings start qualifying. */
    private @Nullable Instant bookingWindowFrom;
    /** UTC instant they stop. */
    private @Nullable Instant bookingWindowUntil;
    /** Stay dates that qualify, half-open in the property's local calendar. */
    private @Nullable StayRange stayWindow;
    /** Most times one guest may redeem it. */
    private @Nullable Integer perGuestRedemptionLimit;
    /** Most times it may be redeemed in total. */
    private @Nullable Integer totalRedemptionLimit;
    /** Lowercase hex SHA-256 of the terms, so an identical resubmission is recognisable. */
    private String contentDigest;
    /** Whether the terms may be cited, and whether they may still be edited. */
    private PublicationState publicationState;
    /** UTC instant they were published and became immutable. */
    private @Nullable Instant publishedAt;
    /** Account holder who approved them; required once published. */
    private @Nullable UUID approvedBy;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Reports whether a booking made at an instant falls inside the booking window.
     *
     * @param instant the command's decision instant
     * @return {@code true} when the instant is within the window, or the window is open-ended
     */
    public boolean acceptsBookingAt(Instant instant) {
        return (bookingWindowFrom == null || !instant.isBefore(bookingWindowFrom))
                && (bookingWindowUntil == null || instant.isBefore(bookingWindowUntil));
    }
}
