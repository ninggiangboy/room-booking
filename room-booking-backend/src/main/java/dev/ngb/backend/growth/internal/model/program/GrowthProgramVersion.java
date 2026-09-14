package dev.ngb.backend.growth.internal.model.program;

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
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.platform.GovernedRegistryStatus;
import dev.ngb.backend.platform.JsonDocument;


/**
 * One published set of terms for a growth programme.
 *
 * <p>Rows are frozen once they leave draft, because they are what people qualified under. A
 * reward that reaches a guest as a discount names migration 019’s promotion version rather than
 * carrying a second discount engine here.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("growth_program_versions")
public class GrowthProgramVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The programme these terms belong to. */
    private UUID growthProgramId;
    /** Position of this version within its programme, unique there. */
    private int versionNumber;
    /** The eligibility rules exactly as they were published. */
    private JsonDocument eligibilityPayload;
    /** The rules in plain words, so a decision taken under them can be explained. */
    private String eligibilityExplanation;
    /** What form the reward takes when somebody qualifies. */
    private GrowthRewardKind rewardKind;
    /** Fixed reward, in integer minor units of its currency. */
    private @Nullable Long rewardAmountMinor;
    /** Reward expressed as a share, used by commission programmes. */
    private @Nullable BigDecimal rewardPercent;
    /** ISO 4217 alphabetic code the reward is denominated in. */
    private @Nullable String rewardCurrency;
    /** Ceiling on any one reward, in integer minor units. */
    private @Nullable Long maximumRewardMinor;
    /** Migration 019 promotion version a discount reward reaches the guest through. */
    private @Nullable UUID promotionVersionId;
    /** How long granted credit remains spendable. */
    private @Nullable Integer creditValidityDays;
    /** What has to happen before the reward is owed. */
    private GrowthQualificationEvent qualificationEvent;
    /** How long after qualifying the reward is held before it can be granted. */
    private int maturityDelayDays;
    /** How many rewards one participant may earn under this version. */
    private @Nullable Integer perSubjectRewardLimit;
    /** How many rewards this version will hand out in total. */
    private @Nullable Integer programRewardLimit;
    /** Total budget for this version, in integer minor units. */
    private @Nullable Long budgetTotalMinor;
    /** ISO 4217 alphabetic code the budget is denominated in. */
    private @Nullable String budgetCurrency;
    /** Digest of the published terms, so they can be shown later to be unchanged. */
    private String contentDigest;
    /** UTC instant these terms begin to apply. */
    private Instant effectiveFrom;
    /** UTC instant these terms stop applying. */
    private @Nullable Instant effectiveUntil;
    /** Operator who approved publication. */
    private @Nullable UUID approvedBy;
    /** Where this version stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the version was published. */
    private @Nullable Instant publishedAt;
    /** UTC instant the version was retired. */
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
