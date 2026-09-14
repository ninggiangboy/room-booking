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
 * One side of the reward owed for a qualified referral.
 *
 * <p>The reward matures before it can be granted, and a granted reward must be reversed before
 * the referral behind it can be closed: a cancelled booking that leaves a reward outstanding has
 * simply paid somebody for a stay that never happened.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("referral_reward_grants")
public class ReferralRewardGrant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The referral this reward pays for. */
    private UUID referralAttributionId;
    /** The terms the reward amount comes from. */
    private UUID growthProgramVersionId;
    /** Whether this is the referrer’s half of the reward or the referee’s. */
    private ReferralBeneficiarySide beneficiarySide;
    /** The person the reward goes to. */
    private UUID beneficiaryHolderId;
    /** Whether the reward arrives as credit or as a discount. */
    private ReferralRewardKind rewardKind;
    /** Reward, in integer minor units of its currency. */
    private long amountMinor;
    /** ISO 4217 alphabetic code the reward is denominated in. */
    private String currency;
    /** The credit lot the reward landed in. */
    private @Nullable UUID storedValueLotId;
    /** The migration 019 redemption the reward landed in. */
    private @Nullable UUID promotionRedemptionId;
    /** Where the reward stands. */
    private ReferralRewardState state;
    /** UTC instant the reward may be granted, not before. */
    private Instant maturesAt;
    /** UTC instant the reward reached the beneficiary. */
    private @Nullable Instant grantedAt;
    /** UTC instant the reward was taken back. */
    private @Nullable Instant reversedAt;
    /** Approved reason code recording why it was taken back. */
    private @Nullable String reversalReason;
    /** Approved reason code recording why it was never granted. */
    private @Nullable String forfeitReason;
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
