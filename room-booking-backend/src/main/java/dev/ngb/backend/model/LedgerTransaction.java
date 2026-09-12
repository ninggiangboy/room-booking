package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * One approved economic event, and the balanced set of postings that express it.
 *
 * <p>The unique key on book, source type, source id and posting purpose is the single defence against
 * a replayed event, a duplicated webhook, a retried worker, and a client timeout each posting the
 * same money again. {@link #sourceInputHash} is stored beside it so that a second request under the
 * same key but with different facts is a recognisable conflict rather than a silent absorption.</p>
 *
 * <p>Because nothing may be added to a posted transaction, the posting service writes this row
 * un-posted, writes its postings, and moves it to {@code POSTED} within one database transaction. A
 * deferred constraint trigger then checks at {@code COMMIT} that debits equal credits, that there
 * are at least two postings, and that they share the transaction's own currency. Once posted the
 * row is frozen; a mistake is answered by a reversal and a new transaction.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ledger_transactions")
public class LedgerTransaction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Book the entry belongs to. */
    private UUID accountingBookId;
    /** Entity whose position it affects. */
    private UUID legalEntityId;
    /** Period it posts into; required once posted. */
    private @Nullable UUID accountingPeriodId;
    /** ISO 4217 code shared by every posting. */
    private String currency;
    /** Kind of fact that authorised the entry. */
    private String sourceType;
    /** Identity of that fact. */
    private UUID sourceId;
    /** Version of the fact that was read. */
    private int sourceVersion;
    /** SHA-256 of the canonical source input, lowercase hex. */
    private String sourceInputHash;
    /** Which accounting effect of that fact this entry is. */
    private String postingPurpose;
    /** Rule version that produced the postings, pinned at first processing. */
    private UUID postingRuleVersionId;
    /** UTC instant the underlying business fact happened. */
    private Instant occurredAt;
    /** Date the entry posts to under finance policy. */
    private LocalDate accountingDate;
    /** IANA zone that accounting date was derived in. */
    private String accountingTimezone;
    /** How far the entry has got. */
    private LedgerTransactionState state;
    /** Short finance-facing explanation; never carries sensitive identifiers. */
    private @Nullable String description;
    /** Structured reason, where the purpose needs one. */
    private @Nullable String reasonCode;
    /** Why the entry was refused, when it was. */
    private @Nullable String rejectionCode;
    /** Transaction this one negates, when it is a reversal. */
    private @Nullable UUID reversesTransactionId;
    /** Transaction this one replaces after a reversal. */
    private @Nullable UUID supersedesTransactionId;
    /** Person who caused the entry, when a person did. */
    private @Nullable UUID actorId;
    /** Worker or service that posted it. */
    private @Nullable String processName;
    /** Approval that authorised it, where one was required. */
    private @Nullable String approvalReference;
    /** Correlation identifier for tracing. */
    private @Nullable UUID correlationId;
    /** Identifier of the message that caused it. */
    private @Nullable UUID causationId;
    /** UTC instant it became part of the journal. */
    private @Nullable Instant postedAt;
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
     * Whether this entry is part of the journal and therefore frozen.
     *
     * @return true once the entry has been posted
     */
    public boolean isPosted() {
        return state == LedgerTransactionState.POSTED;
    }

    /**
     * Whether the entry can still be worked on by the posting service.
     *
     * @return true while the entry is neither posted nor rejected
     */
    public boolean isOpen() {
        return state == LedgerTransactionState.RECEIVED
                || state == LedgerTransactionState.VALIDATED
                || state == LedgerTransactionState.REVIEW_REQUIRED;
    }

    /**
     * Whether this entry exists only to negate another.
     *
     * @return true when it names a transaction it reverses
     */
    public boolean isReversal() {
        return reversesTransactionId != null;
    }
}
