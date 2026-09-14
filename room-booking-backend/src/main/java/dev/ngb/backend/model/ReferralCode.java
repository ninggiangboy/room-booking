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
 * One person’s referral code under one set of programme terms.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("referral_codes")
public class ReferralCode {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The referral terms this code was issued under. */
    private UUID growthProgramVersionId;
    /** The person whose code it is and who earns from it. */
    private UUID ownerAccountHolderId;
    /** The shareable code itself, unique across the platform. */
    private String code;
    /** UTC instant the code was issued. */
    private Instant issuedAt;
    /** UTC instant the code stops working. */
    private @Nullable Instant expiresAt;
    /** Where the code stands. */
    private ReferralCodeState state;
    /** Approved reason code recording why the code was suspended or revoked. */
    private @Nullable String suspensionReason;
    /** How many invitations have been sent under this code. */
    private int invitationCount;
    /** How many referrals under this code have qualified. */
    private int qualifiedCount;
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
