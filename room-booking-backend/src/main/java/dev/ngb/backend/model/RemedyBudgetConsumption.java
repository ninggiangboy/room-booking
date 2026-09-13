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
 * Append-only proof of what one remedy line took from a budget window.
 *
 * <p>The window carries the running total for speed; these rows carry the audit it can be rebuilt from,
 * which matters the first time the two disagree.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("remedy_budget_consumptions")
public class RemedyBudgetConsumption {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The remedy budget window this row belongs to. */
    private UUID remedyBudgetWindowId;
    /** The case remedy line this row belongs to. */
    private UUID caseRemedyLineId;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Whether this entry reserves, consumes, releases or reverses. */
    private BudgetEntryKind entryKind;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** Amount, in integer minor units of its currency. */
    private long amountMinor;
    /** The authorized by account holder this row belongs to. */
    private @Nullable UUID authorizedByAccountHolderId;
    /** Reference to the approval, held in its owning system rather than copied here. */
    private @Nullable String approvalReference;
    /** UTC instant recorded. */
    private Instant recordedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
