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
 * One guest’s balance of one kind of stored value in one currency.
 *
 * <p>The balance and the reservation against it are what stop two open quotes spending the same
 * credit, and the ledger account named here is where the liability actually lives.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("stored_value_accounts")
public class StoredValueAccount {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The person whose balance this is. */
    private UUID accountHolderId;
    /** What kind of stored value this balance holds. */
    private StoredValueAccountKind accountKind;
    /** ISO 4217 alphabetic code the balance is denominated in. */
    private String currency;
    /** ISO 3166-1 alpha-2 market the balance belongs to. */
    private @Nullable String marketCode;
    /** Book the liability for this balance is recorded in. */
    private UUID accountingBookId;
    /** Ledger account the balance is carried as a liability on. */
    private UUID liabilityAccountId;
    /** Value held, in integer minor units of the currency. */
    private long balanceMinor;
    /** Part of the balance already held against an open quote. */
    private long reservedMinor;
    /** Everything ever granted to this balance, in integer minor units. */
    private long lifetimeGrantedMinor;
    /** Everything ever spent from this balance, in integer minor units. */
    private long lifetimeRedeemedMinor;
    /** Everything ever expired from this balance, in integer minor units. */
    private long lifetimeExpiredMinor;
    /** Where the balance stands. */
    private StoredValueAccountState lifecycleState;
    /** Approved reason code recording why the balance was frozen. */
    private @Nullable String frozenReason;
    /** UTC instant the balance was opened. */
    private Instant openedAt;
    /** UTC instant the balance was closed, which requires it to be empty. */
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
