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
 * The record that an authoritative domain command honoured a decision.
 *
 * <p>Risk advises and domains enforce, so this row is the only place the two halves meet -- and the
 * only thing that separates "the booking was denied" from "the booking was never created". A trigger
 * refuses a command claiming it proceeded on a decision that denied, one honouring a decision that
 * had expired or been superseded, and one recorded before its decision was taken. Doing something
 * else is permitted, provided it is recorded as a divergence with a stated reason. Append-only.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_decision_enforcements")
public class RiskDecisionEnforcement {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The decision that was enforced. */
    private UUID riskDecisionId;
    /** The domain that enforced it. */
    private RiskEnforcementDomain enforcingDomain;
    /** Which command. */
    private String commandType;
    /** Which instance of it; one command enforces one decision. */
    private UUID commandId;
    /** What the domain actually did. */
    private EnforcementResult enforcementResult;
    /** The restriction applied as part of enforcing, where one was. */
    private @Nullable UUID appliedRestrictionId;
    /** Why the domain did something else; required for a divergence. */
    private @Nullable String divergenceReason;
    /** When the command ran. */
    private Instant enforcedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
