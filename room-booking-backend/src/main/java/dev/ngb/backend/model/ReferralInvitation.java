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
 * One invitation sent under a referral code.
 *
 * <p>An addressed invitation stores a digest of the address and never the address itself: the
 * platform needs to know it has already written to this person, not to keep a contact list of
 * people who never joined.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("referral_invitations")
public class ReferralInvitation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The code this invitation was sent under. */
    private UUID referralCodeId;
    /** How the invitation was delivered. */
    private ReferralInvitationChannel invitedChannel;
    /** Digest of the address invited; the address itself is never stored here. */
    private @Nullable String invitedContactDigest;
    /** Migration 024 notification intent that carried it. */
    private @Nullable UUID notificationIntentId;
    /** Where the invitation stands. */
    private ReferralInvitationState state;
    /** Approved reason code recording why it was never sent. */
    private @Nullable String suppressionReason;
    /** UTC instant the invitation was sent. */
    private Instant invitedAt;
    /** UTC instant the invitation was first opened. */
    private @Nullable Instant viewedAt;
    /** UTC instant somebody signed up through it. */
    private @Nullable Instant acceptedAt;
    /** UTC instant the invitation stops being acceptable. */
    private Instant expiresAt;
    /** The account created or signed in through the invitation. */
    private @Nullable UUID acceptedAccountHolderId;
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
