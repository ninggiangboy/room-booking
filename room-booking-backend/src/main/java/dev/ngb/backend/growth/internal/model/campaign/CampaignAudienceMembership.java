package dev.ngb.backend.growth.internal.model.campaign;

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
 * One person’s place in one campaign audience.
 *
 * <p>Being in an audience is not permission to be contacted: the row names the consent that
 * permits it, and the holdout arm can never be contacted at all.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("campaign_audience_memberships")
public class CampaignAudienceMembership {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The campaign this person is in. */
    private UUID growthCampaignId;
    /** The person, at most once per campaign. */
    private UUID accountHolderId;
    /** Whether this person is contacted or deliberately held out. */
    private CampaignArm arm;
    /** Migration 030 assignment that put them in that arm. */
    private @Nullable UUID experimentAssignmentId;
    /** The decision that put them in the audience. */
    private UUID eligibilityEvaluationId;
    /** The consent that permits contacting them. */
    private @Nullable UUID communicationConsentId;
    /** Where this membership stands. */
    private CampaignMembershipState state;
    /** Approved reason code recording why they will not be contacted. */
    private @Nullable String suppressionReason;
    /** UTC instant they entered the audience. */
    private Instant enteredAt;
    /** How many messages they have been sent under this campaign. */
    private int contactCount;
    /** UTC instant of the most recent message. */
    private @Nullable Instant lastContactedAt;
    /** UTC instant they did the thing the campaign asked for. */
    private @Nullable Instant convertedAt;
    /** The booking counted as the conversion. */
    private @Nullable UUID conversionBookingId;
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
