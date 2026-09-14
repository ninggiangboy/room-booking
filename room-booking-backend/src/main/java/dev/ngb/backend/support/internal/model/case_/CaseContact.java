package dev.ngb.backend.support.internal.model.case_;

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


/**
 * One inbound or outbound interaction with a participant.
 *
 * <p>Kept apart from internal notes so an internal observation can never be delivered to a participant
 * by being mistaken for a message.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_contacts")
public class CaseContact {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Direction. */
    private ContactDirection direction;
    /** Channel. */
    private CaseContactChannel channel;
    /** Purpose. */
    private ContactPurpose purpose;
    /** The participant this row belongs to. */
    private @Nullable UUID participantId;
    /** Which author actor type this row carries. */
    private ContactAuthorType authorActorType;
    /** The author account holder this row belongs to. */
    private @Nullable UUID authorAccountHolderId;
    /** Where the content lives in its owning domain; support keeps a reference. */
    private @Nullable String contentReference;
    /** The conversation this row belongs to. */
    private @Nullable UUID conversationId;
    /** The message this row belongs to. */
    private @Nullable UUID messageId;
    /** The notification intent this row belongs to. */
    private @Nullable UUID notificationIntentId;
    /** Stable key naming the template. */
    private @Nullable String templateKey;
    /** Which version of the template applies. */
    private @Nullable Integer templateVersion;
    /** BCP 47 locale the content is written in. */
    private @Nullable String locale;
    /** Which visibility scope this row carries. */
    private ContactVisibility visibilityScope;
    /** Where the delivery stands. */
    private ContactDeliveryState deliveryState;
    /** Reference to the delivery, held in its owning system rather than copied here. */
    private @Nullable String deliveryReference;
    /** Reference to the consent, held in its owning system rather than copied here. */
    private @Nullable String consentReference;
    /** Whether consent to record was captured, which a call always states. */
    private @Nullable Boolean recordingConsent;
    /** UTC instant occurred. */
    private Instant occurredAt;
    /** UTC instant received. */
    private Instant receivedAt;
    /** The source event this row belongs to. */
    private @Nullable UUID sourceEventId;
    /** The contact this one corrects. */
    private @Nullable UUID correctsContactId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
