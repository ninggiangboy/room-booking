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
import org.springframework.data.relational.core.mapping.Table;


/**
 * One movement of a stored-value balance.
 *
 * <p>Append-only, and every movement of the balance itself cites the ledger transaction that
 * posted it. The running balance is written in the same transaction as the movement that produced
 * it, so the chain can be reconciled against the ledger afterwards.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("stored_value_entries")
public class StoredValueEntry {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The balance this movement applies to. */
    private UUID storedValueAccountId;
    /** The lot the value moved out of or into. */
    private @Nullable UUID storedValueLotId;
    /** What kind of movement this is. */
    private StoredValueEntryKind entryKind;
    /** Signed change to the balance, in integer minor units. */
    private long balanceDeltaMinor;
    /** Signed change to the reserved part of the balance. */
    private long reservedDeltaMinor;
    /** Balance once this movement had been applied. */
    private long balanceAfterMinor;
    /** Reserved amount once this movement had been applied. */
    private long reservedAfterMinor;
    /** ISO 4217 alphabetic code the movement is denominated in. */
    private String currency;
    /** The quote the value was held against. */
    private @Nullable UUID quoteId;
    /** The booking the value paid for. */
    private @Nullable UUID bookingId;
    /** The hold this movement belongs to. */
    private @Nullable UUID storedValueHoldId;
    /** The posting behind this movement; every change of balance has one. */
    private @Nullable UUID ledgerTransactionId;
    /** The earlier movement this one undoes. */
    private @Nullable UUID reversesEntryId;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String reasonCode;
    /** UTC instant the movement happened. */
    private Instant occurredAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
