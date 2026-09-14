package dev.ngb.backend.growth.internal.model.storedvalue;

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
 * One gift card: value somebody already paid for.
 *
 * <p>The expiry column has to agree with the declared breakage policy, because several markets
 * forbid gift-card expiry outright. Redemption moves the whole face value into one credit lot in
 * the same currency.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("gift_cards")
public class GiftCard {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The gift-card terms this card was issued under. */
    private UUID growthProgramVersionId;
    /** Serial the card is known by, unique across the platform. */
    private String serialReference;
    /** Digest of the redemption code; the code itself is never stored. */
    private String codeDigest;
    /** The legal entity that owes the value on this card. */
    private UUID issuingLegalEntityId;
    /** ISO 3166-1 alpha-2 market whose rules the card was issued under. */
    private String marketCode;
    /** Value of the card, in integer minor units of its currency. */
    private long faceValueMinor;
    /** ISO 4217 alphabetic code the card is denominated in. */
    private String currency;
    /** The person who bought it, where a signed-in person did. */
    private @Nullable UUID purchaserAccountHolderId;
    /** Reference to the payment that bought it, held in its owning system. */
    private @Nullable String purchasePaymentReference;
    /** Digest of the address it was sent to. */
    private @Nullable String recipientContactDigest;
    /** The person it was addressed to, whose balance it may be redeemed into. */
    private @Nullable UUID recipientHolderId;
    /** What happens to unspent value, which some markets constrain. */
    private GiftCardBreakagePolicy breakagePolicy;
    /** UTC instant the card was issued. */
    private Instant issuedAt;
    /** UTC instant the card became redeemable. */
    private @Nullable Instant activatedAt;
    /** UTC instant the card expires; absent where the policy forbids expiry. */
    private @Nullable Instant expiresAt;
    /** UTC instant the card was redeemed. */
    private @Nullable Instant redeemedAt;
    /** The credit lot the face value moved into. */
    private @Nullable UUID redeemedIntoLotId;
    /** UTC instant unspent value was recognised as revenue. */
    private @Nullable Instant breakageRecognizedAt;
    /** The posting that recognised it. */
    private @Nullable UUID breakageTransactionId;
    /** Where the card stands. */
    private GiftCardState state;
    /** Approved reason code recording why the card was cancelled. */
    private @Nullable String voidReason;
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
