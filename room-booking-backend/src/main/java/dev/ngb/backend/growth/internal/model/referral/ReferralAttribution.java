package dev.ngb.backend.growth.internal.model.referral;

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

/**
 * One person recorded as having been referred by another.
 *
 * <p>A referrer may not be the referee, and a person is referred once per programme. Where
 * screening found a shared device, contact or payment instrument, qualifying anyway requires a
 * named override rather than a silent pass.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("referral_attributions")
public class ReferralAttribution {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The programme under which this person is counted as referred, once only. */
    private UUID growthProgramId;
    /** The code that produced the referral. */
    private UUID referralCodeId;
    /** The invitation it came from, where there was one. */
    private @Nullable UUID referralInvitationId;
    /** The person who referred, and who owns the code. */
    private UUID referrerAccountHolderId;
    /** The person who was referred. */
    private UUID refereeAccountHolderId;
    /** How the referral was established. */
    private ReferralAttributionBasis attributionBasis;
    /** UTC instant the referral was recorded. */
    private Instant attributedAt;
    /** Where the referral stands. */
    private ReferralAttributionState state;
    /** The booking that made the referral qualify. */
    private @Nullable UUID qualifyingBookingId;
    /** UTC instant the referral qualified. */
    private @Nullable Instant qualifiedAt;
    /** Approved reason code recording why the referral was refused. */
    private @Nullable String rejectionReason;
    /** Approved reason code recording why a qualified referral was undone. */
    private @Nullable String reversalReason;
    /** UTC instant the referral was undone. */
    private @Nullable Instant reversedAt;
    /** Whether screening found the two parties sharing a device. */
    private boolean sharedDeviceSignal;
    /** Whether screening found the two parties sharing a contact address. */
    private boolean sharedContactSignal;
    /** Whether screening found the two parties sharing a payment instrument. */
    private boolean sharedInstrumentSignal;
    /** Approved reason code recording why a flagged referral was allowed to qualify anyway. */
    private @Nullable String fraudOverrideReason;
    /** Operator who took that decision. */
    private @Nullable UUID fraudOverrideBy;
    /** The anti-abuse checks that were run, as they were run. */
    private JsonDocument screeningPayload;
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
