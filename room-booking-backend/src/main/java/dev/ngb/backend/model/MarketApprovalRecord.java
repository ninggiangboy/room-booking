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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Evidence that a named operator approved, refused, or revoked a piece of configuration.
 *
 * <p>The subject is referenced by type and identifier rather than by a foreign key per kind, so one
 * approval trail covers markets, entities, bundles, capabilities, provider accounts, and content
 * without a column per table.</p>
 *
 * <p>Refusals are recorded as deliberately as approvals: a trail showing only successes could not
 * demonstrate that a proposal was considered and rejected. Records are superseded rather than
 * edited, and there is no {@code @Version} because the row never changes.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_approval_records")
public class MarketApprovalRecord {

    /** Primary key of the approval record. */
    @Id
    private @Nullable UUID id;
    /** Kind of configuration decided upon. */
    private ApprovalSubjectType subjectType;
    /** Identifier of that configuration row. */
    private UUID subjectId;
    /** Whether the subject was approved, refused, or had an approval withdrawn. */
    private ApprovalDecision decision;
    /** Operator accountable for the decision. */
    private UUID approverId;
    /** Role the operator exercised, recorded because roles change over time. */
    private String approverRole;
    /** Why the decision was reached, in the approver's own words. */
    private String reason;
    /** Reference to supporting evidence in document storage. */
    private @Nullable String evidenceReference;
    /** UTC instant the decision was made. */
    private Instant decidedAt;
    /** Record that replaced this one. */
    private @Nullable UUID supersededBy;
}
