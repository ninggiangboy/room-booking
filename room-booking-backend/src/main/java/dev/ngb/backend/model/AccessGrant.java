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
 * Platform authorization for one person to enter one place during one window.
 *
 * <p>A provider credential is one way of honouring a grant, not the grant itself. Keeping them apart
 * is what lets a lock outage fall back to an in-person handoff without rewriting the entitlement, and
 * what lets an unresolved revocation be treated as possibly still open.</p>
 *
 * <p>No secret is stored here. The reference names an envelope-encrypted artifact and the key needed
 * to open it; plaintext exists only in the response to an authorized reveal.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("access_grants")
public class AccessGrant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stay this entitlement belongs to. */
    private UUID operationalStayId;
    /** Booking behind it. */
    private UUID bookingId;
    /** Revision that justified the window. */
    private UUID bookingRevisionId;
    /** Person entitled to enter. */
    private UUID subjectAccountHolderId;
    /** In what capacity they enter. */
    private AccessPartyRole partyRole;
    /** Property. */
    private UUID propertyId;
    /** Specific unit, where supply is unit-addressed. */
    private @Nullable UUID physicalUnitId;
    /** How the entitlement is honoured at the door. */
    private AccessMode accessMode;
    /** Provider account fulfilling it, for credential modes. */
    private @Nullable UUID providerAccountId;
    /** Provider-native device identifier. */
    private @Nullable String deviceReference;
    /** Instruction version the guest was given alongside it. */
    private @Nullable UUID instructionSetId;
    /** Access policy that authorized this window. */
    private String accessPolicyVersion;
    /** Earliest instant entry is authorized. */
    private Instant validFrom;
    /** Latest instant entry is authorized. */
    private Instant validUntil;
    /** Where the entitlement stands. */
    private AccessGrantState state;
    /** Reference to the encrypted credential artifact. */
    private @Nullable String secretReference;
    /** Reference to the key that opens it. */
    private @Nullable String secretKeyReference;
    /** When the credential was last shown to somebody. */
    private @Nullable Instant secretLastRevealedAt;
    /** How many times it has been shown. Monotonic, enforced by trigger. */
    private int revealCount;
    /** When withdrawal was asked for. */
    private @Nullable Instant revocationRequestedAt;
    /** When withdrawal was confirmed. */
    private @Nullable Instant revokedAt;
    /** Why it was withdrawn. */
    private @Nullable String revocationReason;
    /** Whether the provider confirmed the withdrawal. */
    private boolean revocationOutcomeKnown;
    /** Approved manual route if fulfilment fails. */
    private @Nullable AccessMode fallbackMode;
    /** Who authorized the fallback. */
    private @Nullable UUID fallbackAuthorizedBy;
    /** When the fallback was authorized. */
    private @Nullable Instant fallbackAuthorizedAt;
    /** Why fulfilment failed. */
    private @Nullable String failureReason;
    /** Replacement grant. */
    private @Nullable UUID supersededByGrantId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this grant could open a door at the given instant.
     *
     * @param at instant to test
     * @return true when active and within its window
     */
    public boolean isUsableAt(Instant at) {
        return state == AccessGrantState.ACTIVE
                && !at.isBefore(validFrom) && at.isBefore(validUntil);
    }

    /**
     * Whether operations must still treat the credential as possibly live.
     *
     * <p>True while a revocation outcome is unresolved. The compromised-access runbook turns on
     * this being answered honestly rather than optimistically.</p>
     *
     * @return true when withdrawal was attempted but not confirmed
     */
    public boolean isPossiblyOpen() {
        return (state == AccessGrantState.UNKNOWN || state == AccessGrantState.REVOKING)
                || (revocationRequestedAt != null && !revocationOutcomeKnown);
    }

    /**
     * Whether this mode is honoured by a person rather than a device.
     *
     * @return true for handover and desk collection modes
     */
    public boolean isManualMode() {
        return accessMode == AccessMode.IN_PERSON_HANDOFF
                || accessMode == AccessMode.FRONT_DESK
                || accessMode == AccessMode.LOCKBOX;
    }
}
