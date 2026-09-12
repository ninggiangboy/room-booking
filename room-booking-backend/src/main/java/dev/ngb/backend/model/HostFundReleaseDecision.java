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
import org.springframework.data.relational.core.mapping.Table;

/**
 * Why an allocation was, or was not, released.
 *
 * <p>Written every time the question is asked, not only when the answer is yes. A host who asks why
 * their money is not available gets reason codes and the versions of the inputs that produced them,
 * rather than an amount that quietly vanished from the balance.</p>
 *
 * <p>Append-only: a decision that turns out to have been wrong is followed by another decision, never
 * rewritten. The row therefore carries no optimistic lock and no update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_fund_release_decisions")
public class HostFundReleaseDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Allocation the decision is about. */
    private UUID payableAllocationId;
    /** UTC instant the evaluation ran. */
    private Instant evaluatedAt;
    /** The answer. */
    private FundReleaseDecision decision;
    /** Reason codes explaining it; required for anything but a release. */
    private JsonDocument reasonCodes;
    /** Policy version that was applied. */
    private @Nullable UUID releasePolicyVersionId;
    /** Versions of every input the evaluation read. */
    private JsonDocument inputVersions;
    /** SHA-256 of the canonical inputs, lowercase hex, so the decision can be reproduced. */
    private String inputHash;
    /** UTC instant the question should be asked again. */
    private @Nullable Instant nextReviewAt;
    /** Person who forced the evaluation, when one did. */
    private @Nullable UUID actorId;
    /** Worker that ran it. */
    private @Nullable String processName;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
