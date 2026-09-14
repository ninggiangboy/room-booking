package dev.ngb.backend.support.internal.model.remedy;

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
 * A bounded, structured proposal between two parties.
 *
 * <p>Acceptance is an explicit signed-in command against an exact offer version and digest. Silence, a
 * read receipt, an agent's note and a friendly free-text reply are none of them acceptance.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_offers")
public class CaseOffer {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Position of this row within its parent, unique there. */
    private short offerNumber;
    /** Which version of the offer applies. */
    private int offerVersion;
    /** Which proposer kind this row carries. */
    private OfferProposerKind proposerKind;
    /** The proposer account holder this row belongs to. */
    private @Nullable UUID proposerAccountHolderId;
    /** The recipient account holder this row belongs to. */
    private UUID recipientAccountHolderId;
    /** The offer this one counters. */
    private @Nullable UUID countersOfferId;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** Total amount, in integer minor units of its currency. */
    private long totalAmountMinor;
    /** Who the offer assumes will fund it, before authority and funding are validated. */
    private FundingAssumption fundingAssumption;
    /** Non-monetary terms, held outside this row. */
    private @Nullable String nonFinancialTermsReference;
    /** Reference to the policy, held in its owning system rather than copied here. */
    private @Nullable String policyReference;
    /** Evidence referenced by the offer. */
    private UUID[] evidenceItemIds;
    /** Confidentiality copy attached to the offer, where a market allows it. */
    private @Nullable String confidentialityTermsKey;
    /** Digest of the exact terms offered; acceptance must match it. */
    private String contentDigest;
    /** Where the state stands. */
    private CaseOfferState state;
    /** UTC instant sent. */
    private @Nullable Instant sentAt;
    /** UTC instant viewed. */
    private @Nullable Instant viewedAt;
    /** UTC instant expires. */
    private @Nullable Instant expiresAt;
    /** UTC instant responded. */
    private @Nullable Instant respondedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String rejectionReason;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String withdrawalReason;
    /** The accepted by account holder this row belongs to. */
    private @Nullable UUID acceptedByAccountHolderId;
    /** The digest the accepting party saw, which must equal the offer’s own. */
    private @Nullable String acceptedDigest;
    /** The authenticated session behind the acceptance. */
    private @Nullable String acceptanceAuthenticationReference;
    /** The offer version accepted, which must equal the current one. */
    private @Nullable Integer acceptedOfferVersion;
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
