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
 * What the host did about advice they were shown.
 * <p>It cites the disclosure it answers, so an acceptance can never be recorded for advice the host
 * was never shown the basis of. Refusals and lapses are recorded as deliberately as acceptances: a
 * system that only stores acceptances cannot tell a good recommendation from one nobody dared
 * refuse.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_advice_decisions")
public class HostAdviceDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /**
     * The disclosure this decision answers; a decision cannot be recorded for advice the host was
     * never shown the basis of.
     */
    private UUID disclosureId;
    /** What the host did about the advice. */
    private AdviceDecisionOutcome outcome;
    /** Who decided: the host, a co-host, or the platform observing that nothing was decided. */
    private AdviceActorRole actorRole;
    /** The person who decided, absent when the platform merely observed the advice lapse. */
    private @Nullable UUID decidedBy;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String reasonCode;
    /** Anything the host wrote alongside the reason code. */
    private @Nullable String reasonText;
    /** What the host changed, where they took the advice but not as offered. */
    private @Nullable String modificationSummary;
    /** Reference to whatever the acceptance produced, held in its owning system. */
    private @Nullable String appliedReference;
    /** UTC instant the decision was taken. */
    private Instant decidedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
