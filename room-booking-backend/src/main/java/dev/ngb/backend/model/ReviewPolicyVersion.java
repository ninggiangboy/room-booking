package dev.ngb.backend.model;

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
 * The effective-dated rules a review cycle is judged by.
 *
 * <p>Frozen once published, because every right snapshots the version it was opened under and a
 * historical deadline must never move. Changing the rules means publishing a new version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_policy_versions")
public class ReviewPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identity of the policy across versions. */
    private String policyKey;
    /** Version within that policy. */
    private int policyVersion;
    /** Market it applies to, or null for the platform default. */
    private @Nullable String marketCode;
    /** Locale family it was written for. */
    private @Nullable String localeFamily;
    /** How far this version has progressed. */
    private PolicyVersionStatus status;
    /** When it came into force. */
    private @Nullable Instant effectiveFrom;
    /** When it stopped being in force. */
    private @Nullable Instant effectiveUntil;
    /** Booking outcomes that open a right. */
    private String[] eligibleOutcomes;
    /** Directions a cycle may open. */
    private String[] eligibleDirections;
    /** How long an author has to submit. */
    private short submissionWindowDays;
    /** How that window resolves to an instant. */
    private DeadlineConvention deadlineConvention;
    /** When a submitted review may become visible. */
    private ReviewRevealRule revealRule;
    /** How long one side may be held before the other publishes anyway. */
    private @Nullable Short maximumModerationHoldHours;
    /** How long an author may replace their submission. */
    private ReviewEditRule editRule;
    /** Whether an author may take a review back. */
    private boolean withdrawalAllowed;
    /** How long a host has to respond publicly. */
    private @Nullable Short responseWindowDays;
    /** Rating schema in force. */
    private int ratingSchemaVersion;
    /** Where the category definitions live. */
    private @Nullable String categorySchemaReference;
    /** Aggregation rule the public average follows. */
    private int aggregateRuleVersion;
    /** How the displayed value is rounded. */
    private RatingRoundingRule displayRoundingRule;
    /** Market or legal disclosure copy shown to the author. */
    private @Nullable String disclosureVersion;
    /** Reminder schedule this policy uses. */
    private @Nullable String reminderScheduleFamily;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountHolderId;
    /** When it was approved. */
    private @Nullable Instant approvedAt;
    /** Hash of the whole version. */
    private String contentHash;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this version may be used to open new cycles.
     *
     * @param at instant to test
     * @return true when published and within its effective interval
     */
    public boolean isInForceAt(Instant at) {
        return status == PolicyVersionStatus.PUBLISHED
                && effectiveFrom != null && !at.isBefore(effectiveFrom)
                && (effectiveUntil == null || at.isBefore(effectiveUntil));
    }

    /**
     * Whether this policy seals reviews until both sides have spoken.
     *
     * @return true for the double-blind rule
     */
    public boolean isDoubleBlind() {
        return revealRule == ReviewRevealRule.DOUBLE_BLIND;
    }
}
