package dev.ngb.backend.growth.internal.model.affiliate;

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
 * One click by a partner, and the booking it may claim.
 *
 * <p>The attribution window comes from the agreement rather than from the click, and a booking is
 * credited to at most one partner: without that, two partners each holding a click both invoice
 * for the same stay.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("affiliate_attributions")
public class AffiliateAttribution {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The partner whose link was followed. */
    private UUID affiliatePartnerId;
    /** The partner’s own reference for the click, unique per partner. */
    private String clickReference;
    /** Where in the product the visitor landed. */
    private String landingSurface;
    /** Pseudonymous key standing for the visitor. */
    private @Nullable String anonymousUnitKey;
    /** The person, once the visitor signed in. */
    private @Nullable UUID accountHolderId;
    /** UTC instant the link was followed. */
    private Instant clickedAt;
    /** UTC instant the claim lapses, set by the agreement and not by the partner. */
    private Instant expiresAt;
    /** The booking credited to this click, at most one partner per booking. */
    private @Nullable UUID bookingId;
    /** UTC instant the booking was credited. */
    private @Nullable Instant attributedAt;
    /** Where the claim stands. */
    private AffiliateAttributionState state;
    /** Approved reason code recording why the claim was refused. */
    private @Nullable String rejectionReason;
    /** What the abuse checks found, as they found it. */
    private JsonDocument fraudSignals;
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
