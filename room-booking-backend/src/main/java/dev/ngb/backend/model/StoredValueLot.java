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
 * One grant of stored value, with its own origin, expiry and restrictions.
 *
 * <p>Credit is held as lots rather than as a single number because expiry belongs to the money:
 * a balance that cannot say which promise it came from cannot say which promise expired.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("stored_value_lots")
public class StoredValueLot {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The balance this lot forms part of. */
    private UUID storedValueAccountId;
    /** Where the value came from. */
    private StoredValueSourceKind sourceKind;
    /** The programme terms that granted it, where a programme did. */
    private @Nullable UUID growthProgramVersionId;
    /** The row in the granting domain this lot came from. */
    private @Nullable UUID sourceReferenceId;
    /** Value granted, in integer minor units of the currency. */
    private long originalMinor;
    /** Value still spendable from this lot, in integer minor units. */
    private long remainingMinor;
    /** ISO 4217 alphabetic code the lot is denominated in. */
    private String currency;
    /** UTC instant the value was granted. */
    private Instant grantedAt;
    /** UTC instant this particular promise expires; null means it does not. */
    private @Nullable Instant expiresAt;
    /** Whether this value is returned in cash when a booking paid with it is refunded. */
    private boolean refundable;
    /** Whether this value may be moved to another person. */
    private boolean transferable;
    /** ISO 3166-1 alpha-2 market the value may only be spent in. */
    private @Nullable String restrictedMarketCode;
    /** Smallest booking total this value may be applied to. */
    private @Nullable Long minimumBookingMinor;
    /** The posting that recorded the liability for this grant. */
    private UUID ledgerTransactionId;
    /** Where the lot stands. */
    private StoredValueLotState state;
    /** Approved reason code recording why the lot was taken back. */
    private @Nullable String revokedReason;
    /** UTC instant the lot stopped being spendable. */
    private @Nullable Instant closedAt;
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
