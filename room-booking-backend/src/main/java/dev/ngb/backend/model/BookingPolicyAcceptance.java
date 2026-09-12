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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Evidence that a guest accepted one specific version of one specific policy.
 *
 * <p>Append-only, enforced by a database trigger. A booking that cites a cancellation policy without
 * provable acceptance carries a term the platform cannot enforce and a refund argument it cannot
 * win, and evidence the application can rewrite is not evidence at all.</p>
 *
 * <p>Each policy is recorded separately because they version on different schedules and a dispute is
 * almost always about exactly one of them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_policy_acceptances")
public class BookingPolicyAcceptance {

    /** Primary key of the acceptance. */
    @Id
    private @Nullable UUID id;
    /** Booking the acceptance belongs to. */
    private UUID bookingId;
    /** Checkout attempt during which it was collected. */
    private @Nullable UUID bookingCheckoutId;
    /** Which policy was accepted. */
    private BookingPolicyType policyType;
    /** Stable key of the specific policy document. */
    private String policyKey;
    /** Version of it, so the exact terms can be reconstructed. */
    private String policyVersion;
    /** Digest of the text actually shown, proving what the guest saw rather than what was current. */
    private String disclosureDigest;
    /** Account holder who accepted. */
    private UUID acceptedByAccountHolderId;
    /** Surface the acceptance was collected on. */
    private AcceptanceChannel acceptanceChannel;
    /** Locale the policy was displayed in. */
    private String locale;
    /** Digest of the client address, kept as corroboration without retaining the address. */
    private @Nullable String clientIpDigest;
    /** Digest of the user agent, for the same reason. */
    private @Nullable String userAgentDigest;
    /** UTC instant of acceptance, supplied by the caller's decision clock. */
    private Instant acceptedAt;
}
