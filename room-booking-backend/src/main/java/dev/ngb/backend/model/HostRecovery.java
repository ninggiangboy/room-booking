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
 * What a host owes the platform after money already left, and how it is being collected.
 *
 * <p>A recovery is not a deletion of the original payout. The payout stays in history and the recovery
 * sits beside it with its own notice, contract basis, and appeal state, because a host is entitled to
 * know why their next payout is smaller.</p>
 *
 * <p>One recovery per causal instruction, so the same debt cannot be collected twice, and the database
 * enforces the ceiling: collected plus written off can never exceed what was owed. A write-off is a
 * new approved accounting event with a name attached; it does not delete the receivable, the causal
 * evidence, or the host's history.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_recoveries")
public class HostRecovery {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Short identifier a host and support can both quote. */
    private String publicId;
    /** Host who owes it. */
    private UUID hostAccountHolderId;
    /** Entity owed. */
    private UUID legalEntityId;
    /** Book the debt is accounted in. */
    private UUID accountingBookId;
    /** ISO 4217 code; no cross-currency offset without an approved conversion. */
    private String currency;
    /** Why the debt exists. */
    private HostRecoveryCause cause;
    /** Kind of instruction that created it. */
    private String causalSourceType;
    /** Identity of that instruction; unique with its type. */
    private UUID causalSourceId;
    /** Booking behind it. */
    private @Nullable UUID bookingId;
    /** Payout that was returned or overpaid. */
    private @Nullable UUID payoutInstructionId;
    /** Minor units originally owed. */
    private long originalAmountMinor;
    /** Minor units collected so far. */
    private long recoveredAmountMinor;
    /** Minor units given up under approval. */
    private long writtenOffAmountMinor;
    /** Original less collected less written off. */
    private long remainingAmountMinor;
    /** Policy version choosing the order of collection steps. */
    private @Nullable UUID waterfallPolicyVersionId;
    /** How far collection has got. */
    private HostRecoveryState state;
    /** UTC instant the host was told. */
    private @Nullable Instant noticeSentAt;
    /** Contract clause or consent the collection rests on. */
    private @Nullable String contractBasisReference;
    /** UTC instant the host contested it; survives the dispute being settled. */
    private @Nullable Instant disputedAt;
    /** UTC instant it was given up. */
    private @Nullable Instant writtenOffAt;
    /** Approver of that write-off. */
    private @Nullable UUID writtenOffByActorId;
    /** UTC instant it finished, whether collected or written off. */
    private @Nullable Instant closedAt;
    /** UTC instant it was raised. */
    private Instant openedAt;
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
     * Whether anything is still owed.
     *
     * @return true while the remaining balance is above zero
     */
    public boolean isOutstanding() {
        return remainingAmountMinor > 0;
    }
}
