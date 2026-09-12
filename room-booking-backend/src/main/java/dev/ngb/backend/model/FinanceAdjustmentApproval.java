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
 * One approver's decision about one adjustment request, bound to the exact request they saw.
 *
 * <p>Unique per approver per request, so one person cannot satisfy a two-approval threshold by clicking
 * twice, and a proposer cannot approve their own request.</p>
 *
 * <p>Break-glass is short-lived by construction: an approval that claims it must also say when it stops
 * being valid. Append-only, so a decision somebody regrets is answered by a rejection rather than a
 * rewrite.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("finance_adjustment_approvals")
public class FinanceAdjustmentApproval {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Request being decided. */
    private UUID adjustmentRequestId;
    /** Person deciding. */
    private UUID approverActorId;
    /** What they decided. */
    private FinanceApprovalDecision decision;
    /** Hash of the request as it stood when they saw it. */
    private String approvedRequestHash;
    /** How strongly they were authenticated. */
    private AuthenticationStrength authenticationStrength;
    /** Whether this used an emergency role. */
    private boolean breakGlass;
    /** UTC instant that emergency grant stops being valid. */
    private @Nullable Instant breakGlassExpiresAt;
    /** Why; required for a rejection. */
    private @Nullable String reasonCode;
    /** Short note; redacted of sensitive data. */
    private @Nullable String note;
    /** UTC instant of the decision. */
    private Instant decidedAt;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
