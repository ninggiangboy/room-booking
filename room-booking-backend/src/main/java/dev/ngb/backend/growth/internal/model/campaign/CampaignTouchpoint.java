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
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.messaging.types.NotificationChannel;

import dev.ngb.backend.messaging.types.NotificationChannel;


/**
 * One message sent to one person under one campaign.
 *
 * <p>Append-only, and it carries the notification intent that delivered it rather than a private
 * send path. Each row proves, at the moment it was created, that a consent covering this category
 * and channel was in force and that the campaign’s own frequency cap had not been used up.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("campaign_touchpoints")
public class CampaignTouchpoint {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The audience membership being contacted. */
    private UUID audienceMembershipId;
    /** Position of this message within the membership, unique there. */
    private int touchpointNumber;
    /** Migration 024 intent that carried it; there is no private send path. */
    private UUID notificationIntentId;
    /** Channel the message went out on. */
    private NotificationChannel channel;
    /** The consent relied on at the moment of sending. */
    private UUID communicationConsentId;
    /** UTC instant that consent was checked. */
    private Instant consentCheckedAt;
    /** UTC instant the send was requested. */
    private Instant requestedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
