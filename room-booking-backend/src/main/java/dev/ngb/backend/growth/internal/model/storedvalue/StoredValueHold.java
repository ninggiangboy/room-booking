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
 * Credit set aside against one open quote.
 *
 * <p>Exactly one hold per quote, and every hold expires, so credit is never stranded behind a
 * checkout somebody abandoned.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("stored_value_holds")
public class StoredValueHold {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The balance the credit is held against. */
    private UUID storedValueAccountId;
    /** The quote holding it, at most one hold per quote. */
    private UUID quoteId;
    /** Value held, in integer minor units of its currency. */
    private long amountMinor;
    /** ISO 4217 alphabetic code the hold is denominated in. */
    private String currency;
    /** Where the hold stands. */
    private StoredValueHoldState state;
    /** UTC instant the credit was set aside. */
    private Instant heldAt;
    /** UTC instant the hold lapses, so credit is never stranded. */
    private Instant expiresAt;
    /** UTC instant the hold was consumed, released or expired. */
    private @Nullable Instant resolvedAt;
    /** The booking that consumed the hold. */
    private @Nullable UUID bookingId;
    /** Approved reason code recording why the hold was given back. */
    private @Nullable String releaseReason;
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
