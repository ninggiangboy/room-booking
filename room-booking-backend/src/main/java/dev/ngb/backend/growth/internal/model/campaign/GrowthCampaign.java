package dev.ngb.backend.growth.internal.model.campaign;

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
import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.messaging.types.NotificationChannel;

/**
 * One campaign: an audience, a channel, a cap and a holdout.
 *
 * <p>Leaving draft requires a fairness review and a price-transparency attestation, because that
 * is the point at which the campaign becomes something done to real people.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("growth_campaigns")
public class GrowthCampaign {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The programme terms this campaign runs under. */
    private UUID growthProgramVersionId;
    /** Stable key naming the campaign, unique across the platform. */
    private String campaignKey;
    /** Name the campaign is known by internally. */
    private String displayName;
    /** What the campaign is trying to change. */
    private CampaignObjective objective;
    /** ISO 3166-1 alpha-2 market the campaign runs in. */
    private @Nullable String marketCode;
    /** The audience rule exactly as it was approved. */
    private JsonDocument audienceDefinition;
    /** Who is in the audience, in plain words. */
    private String audienceExplanation;
    /** Consent category a recipient must hold before any send. */
    private String requiredConsentCategory;
    /** Channel the campaign sends on, which the consent must match. */
    private NotificationChannel requiredChannel;
    /** Most messages one person may receive inside the window. */
    private int frequencyCapPerWindow;
    /** How long, in days, the frequency window runs. */
    private int frequencyWindowDays;
    /** Share of the audience deliberately not contacted, so the effect can be measured. */
    private BigDecimal holdoutShare;
    /** Migration 030 experiment the holdout is registered under. */
    private @Nullable UUID experimentDefinitionId;
    /** UTC instant sending may begin. */
    private Instant sendWindowFrom;
    /** UTC instant sending must have stopped. */
    private Instant sendWindowUntil;
    /** Reference to the fairness review, held in its owning system. */
    private @Nullable String fairnessReviewReference;
    /** UTC instant that review concluded. */
    private @Nullable Instant fairnessReviewedAt;
    /** Whether somebody attested the offer is shown at a truthful price. */
    private boolean priceTransparencyAttested;
    /** Where the campaign stands. */
    private CampaignStatus status;
    /** Approved reason code recording why the campaign was called off. */
    private @Nullable String cancellationReason;
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
