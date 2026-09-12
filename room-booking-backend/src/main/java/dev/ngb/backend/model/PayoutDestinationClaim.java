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
 * A claim about where a host's money may be sent, and how well that claim is evidenced.
 *
 * <p>This row says *where* money may go. It never says what a host is owed: entitlement is the
 * ledger's business in migration {@code 022} and is untouched by a destination being rejected or
 * detached. Conflating the two is how a bank-detail problem silently becomes a loss of earnings.</p>
 *
 * <p>{@link #coolingOffUntil} is the control that blunts payout diversion: an attacker who takes over
 * an account must still prove ownership of the destination and then wait, during which the real host
 * has a chance to notice. Only one destination may be active per holder, market, and currency —
 * two would make "where does this payout go" ambiguous at exactly the moment money moves.</p>
 *
 * <p>The account number is held by reference, with a digest of the account name for matching and the
 * last characters so a host can recognize which account this is.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payout_destination_claims")
public class PayoutDestinationClaim {

    /** Primary key of the claim. */
    @Id
    private @Nullable UUID id;
    /** Account holder the money belongs to. */
    private UUID accountHolderId;
    /** Legal profile whose identity must match the destination's owner. */
    private UUID hostLegalProfileId;
    /** Market the destination serves. */
    private String marketCode;
    /** ISO 4217 currency the destination can receive. */
    private String currency;
    /** Payout rail the destination is reachable over. */
    private String railKey;
    /** SHA-256 digest of the account name, for matching against the host's legal name. */
    private String accountNameDigest;
    /** Reference to the protected account details. */
    private String accountReference;
    /** Last characters of the account number, so a host can recognize it. */
    private @Nullable String accountLast4;
    /** Provider account the destination was registered through. */
    private @Nullable String providerAccountKey;
    /** Version of that provider account. */
    private @Nullable Short providerAccountVersion;
    /** How far ownership of the destination has been proven. */
    private PayoutOwnershipState ownershipState;
    /** Reference to the ownership evidence in protected storage. */
    private @Nullable String ownershipEvidenceRef;
    /** UTC instant ownership was proven; paired with the verified state. */
    private @Nullable Instant verifiedAt;
    /** UTC instant until which payouts are withheld after the destination was added. */
    private @Nullable Instant coolingOffUntil;
    /** Whether this is the live destination or a retained historical one. */
    private ExternalReferenceLifecycle lifecycleState;
    /** UTC instant the destination stopped being live. */
    private @Nullable Instant detachedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether money may actually be sent here at the supplied instant.
     *
     * <p>All three conditions are required. A verified destination still inside its cooling-off
     * window must not receive money, which is the entire point of the window.</p>
     *
     * @param instant the payout run's decision instant
     * @return {@code true} when the destination is live, ownership is proven, and cooling-off passed
     */
    public boolean canReceiveAt(Instant instant) {
        return lifecycleState == ExternalReferenceLifecycle.ACTIVE
                && ownershipState == PayoutOwnershipState.VERIFIED
                && (coolingOffUntil == null || !coolingOffUntil.isAfter(instant));
    }
}
