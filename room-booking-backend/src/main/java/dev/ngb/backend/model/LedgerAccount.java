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
 * One account in the chart of accounts, with the policy the posting engine enforces against it.
 *
 * <p>Finance approves the normal balance, the allowed currency, which dimensions a posting must carry
 * and which it must not, and whether manual posting is permitted. An account that has been posted
 * to is retired rather than deleted, because deleting it would break historical replay.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ledger_accounts")
public class LedgerAccount {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Book the account belongs to. */
    private UUID accountingBookId;
    /** Stable code, unique within the book. */
    private String accountCode;
    /** Human-readable name. */
    private String accountName;
    /** Where the account sits in the accounting equation. */
    private LedgerAccountClass accountClass;
    /** Finance grouping such as {@code PROVIDER_CLEARING} or {@code HOST_PAYABLE}. */
    private String accountFamily;
    /** Side that increases this account. */
    private PostingDirection normalBalance;
    /** ISO 4217 code the account is restricted to, when it is. */
    private @Nullable String currency;
    /**
     * Dimensions a posting to this account must carry, as an immutable policy snapshot.
     *
     * <p>Held as a document rather than columns because the dimension set is finance policy and the
     * engine reads it whole. The dimensions themselves stay relational on the posting.</p>
     */
    private JsonDocument requiredDimensions;
    /** Dimensions a posting to this account must not carry. */
    private JsonDocument forbiddenDimensions;
    /** Whether a manual adjustment may target this account. */
    private boolean manualPostingAllowed;
    /** How the account rolls up in finance reporting. */
    private @Nullable String reportMappingCode;
    /** Whether the account may be posted to. */
    private LedgerAccountLifecycle lifecycleState;
    /** UTC instant the account became usable. */
    private Instant effectiveFrom;
    /** UTC instant it stops being usable, when retired forward. */
    private @Nullable Instant effectiveUntil;
    /** Finance actor who approved it. */
    private @Nullable UUID approvedByActorId;
    /** UTC instant of that approval. */
    private @Nullable Instant approvedAt;
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
