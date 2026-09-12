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
 * A pointer to a credential the platform deliberately does not hold.
 *
 * <p>Everything here is either an opaque provider token or display metadata a support agent may
 * read aloud. {@link #maskedSuffix} is four characters wide and checked to be four digits, so a
 * card number cannot fit in it by construction rather than by a reviewer noticing.</p>
 *
 * <p>A token belongs to one provider account and is not portable to another. Revoking one disables
 * new operations without deleting the reference, because historic transactions and open disputes
 * still have to be explainable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_method_references")
public class PaymentMethodReference {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Guest the instrument belongs to. */
    private UUID ownerAccountHolderId;
    /** Merchant account the token is scoped to. */
    private UUID providerAccountId;
    /** Opaque vault or provider token. Never a credential. */
    private String providerToken;
    /** Provider-side customer the token hangs off. */
    private @Nullable String providerCustomerReference;
    /** Instrument family. */
    private PaymentMethodFamily methodFamily;
    /** Safe brand or scheme name for display. */
    private @Nullable String brand;
    /** Where the money behind the instrument comes from. */
    private @Nullable FundingType fundingType;
    /** Last four digits, for recognition. Four characters, digits only. */
    private @Nullable String maskedSuffix;
    /** Expiry month, where the provider permits storing it. */
    private @Nullable Short expiryMonth;
    /** Expiry year, where the provider permits storing it. */
    private @Nullable Short expiryYear;
    /** ISO 3166-1 alpha-2 billing country, stored only where lawful and needed. */
    private @Nullable String billingCountry;
    /** Whether the token may be charged again without the guest present. */
    private boolean reusable;
    /** Provider mandate authorising later collection. */
    private @Nullable String mandateReference;
    /** Record of the consent the guest gave to store and reuse. */
    private @Nullable String consentReference;
    /**
     * UTC instant that consent was recorded.
     *
     * <p>Required for a reusable token. Without it the platform cannot show why it was allowed to
     * charge the guest a second time.</p>
     */
    private @Nullable Instant consentRecordedAt;
    /** Usability of the reference. */
    private PaymentMethodState state;
    /** Retention class governing how long it is kept. */
    private String retentionCategory;
    /** UTC instant it was last charged. */
    private @Nullable Instant lastUsedAt;
    /** UTC instant it was withdrawn. */
    private @Nullable Instant revokedAt;
    /** Why it was withdrawn. */
    private @Nullable String revocationReason;
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
     * Whether this reference may be used for a new operation.
     *
     * @return {@code true} only while it is active
     */
    public boolean isUsable() {
        return state == PaymentMethodState.ACTIVE;
    }
}
