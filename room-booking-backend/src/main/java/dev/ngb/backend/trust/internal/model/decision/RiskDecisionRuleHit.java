package dev.ngb.backend.trust.internal.model.decision;

import java.math.BigDecimal;
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
 * One rule that was evaluated while a decision was being taken.
 *
 * <p>Append-only and ordered: an explanation edited after the fact is not an explanation. A rule
 * registered as explanation-only may describe the answer but never move it, and a rule that errored
 * or was skipped contributed nothing -- both are check constraints, so a failed evaluation cannot be
 * presented later as a finding.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_decision_rule_hits")
public class RiskDecisionRuleHit {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The decision this hit belongs to. */
    private UUID riskDecisionId;
    /** Position in the evaluation order; unique within the decision. */
    private int hitSequence;
    /** Which rule fired. */
    private String ruleId;
    /** How much authority the rule has over the answer. */
    private RuleClass ruleClass;
    /** What happened when it was evaluated. */
    private RuleHitResult result;
    /** Whether it actually moved the answer. */
    private boolean contributedToOutcome;
    /** How much it moved it, where the policy expresses that numerically. */
    private @Nullable BigDecimal contributionWeight;
    /** What it read; references rather than copied values. */
    private String[] inputReferences;
    /** Explanation safe to show a reviewer; no exploitable detector detail. */
    private @Nullable String redactedExplanation;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
