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
 * One legal entity, one accounting purpose, one coherent set of accounts and policies.
 *
 * <p>Every journal transaction names its book and its legal entity, so a second book can be added
 * later without any existing entry becoming ambiguous. Cross-entity movements use due-to and
 * due-from accounts and independently balanced journals; they are never netted into one
 * transaction.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("accounting_books")
public class AccountingBook {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identifier finance refers to the book by. */
    private String bookKey;
    /** Entity whose position this book records. */
    private UUID legalEntityId;
    /** Human-readable name for finance tooling. */
    private String displayName;
    /** What the book is kept for. */
    private AccountingBookPurpose purpose;
    /** ISO 4217 currency the book is denominated in. */
    private String functionalCurrency;
    /** ISO 4217 currency for reporting, when it differs. */
    private @Nullable String reportingCurrency;
    /** IANA zone accounting periods are cut in. */
    private String periodTimezone;
    /** Whether the book accepts postings. */
    private ConfigurationLifecycle lifecycleState;
    /** UTC instant the book started being usable. */
    private Instant effectiveFrom;
    /** UTC instant it stops, when it is time-bounded. */
    private @Nullable Instant effectiveUntil;
    /** Actor accountable for the book. */
    private @Nullable UUID ownerActorId;
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
