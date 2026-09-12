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
 * The verdict on one comparison, with both sides named.
 *
 * <p>A difference is a first-class fact with a name, not the absence of a match. The rule tier that
 * produced the result is stored, so the difference between "the provider object id matched exactly"
 * and "the amounts were close enough" stays visible afterwards.</p>
 *
 * <p>Only the deterministic tiers of the matching ladder may auto-match: the database refuses a
 * {@code MATCHED_EXACT} that claims a bounded or suggested confidence, or a tier beyond four. Fuzzy
 * and model-assisted candidates may be recorded but may never post, close, or write off money.</p>
 *
 * <p>Append-only. A better answer is a new row that supersedes this one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("finance_reconciliation_matches")
public class FinanceReconciliationMatch {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Run that produced the comparison. */
    private UUID runId;
    /** The verdict. */
    private ReconciliationMatchOutcome outcome;
    /** Which rung of the matching ladder produced it, one through six. */
    private short ruleTier;
    /** Version of the rule set applied. */
    private String ruleVersion;
    /** How much the match may be trusted to act on its own. */
    private MatchConfidenceClass confidenceClass;
    /** External row compared; absent for a missing-external result. */
    private @Nullable UUID externalRecordId;
    /** Internal transaction compared. */
    private @Nullable UUID ledgerTransactionId;
    /** Internal posting compared. */
    private @Nullable UUID ledgerPostingId;
    /** Internal payout compared. */
    private @Nullable UUID payoutInstructionId;
    /** Internal payment operation compared. */
    private @Nullable UUID paymentOperationId;
    /** ISO 4217 code of the amounts compared. */
    private @Nullable String currency;
    /** Minor units the platform believes. */
    private @Nullable Long internalAmountMinor;
    /** Minor units the external source reports. */
    private @Nullable Long externalAmountMinor;
    /** The gap between them. */
    private @Nullable Long differenceAmountMinor;
    /** How much the difference matters under the approved policy. */
    private ExceptionSeverity materiality;
    /** Case opened for the difference, when one was. */
    private @Nullable UUID caseId;
    /** Earlier verdict this one replaces. */
    private @Nullable UUID supersedesMatchId;
    /** What should happen next. */
    private @Nullable String nextActionCode;
    /** UTC instant the comparison ran. */
    private Instant matchedAt;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this verdict closes the comparison on its own.
     *
     * @return true only for a deterministic exact or aggregate match
     */
    public boolean isSettled() {
        return outcome == ReconciliationMatchOutcome.MATCHED_EXACT
                || outcome == ReconciliationMatchOutcome.MATCHED_AGGREGATE;
    }
}
