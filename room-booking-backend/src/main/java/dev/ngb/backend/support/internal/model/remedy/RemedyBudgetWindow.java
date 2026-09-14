package dev.ngb.backend.support.internal.model.remedy;

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
 * What an agent, team, campaign or market may spend on goodwill in a period.
 *
 * <p>These limits bound abuse and mistake, and must never reduce a mandatory contractual entitlement,
 * which is why a contractual line is not charged against a budget window at all.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("remedy_budget_windows")
public class RemedyBudgetWindow {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which budget scope this row carries. */
    private BudgetScope budgetScope;
    /** Stable key naming the scope. */
    private String scopeKey;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** Window from. */
    private Instant windowFrom;
    /** Window until. */
    private Instant windowUntil;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** What may be spent in this window, in minor units. */
    private long limitAmountMinor;
    /** Currently held against the limit, in minor units. */
    private long reservedAmountMinor;
    /** Already spent from the limit, in minor units. */
    private long consumedAmountMinor;
    /** Where the state stands. */
    private BudgetWindowState state;
    /** What happens when the limit is reached. */
    private BudgetBreachBehaviour breachBehaviour;
    /** Escalation route. */
    private @Nullable String escalationRoute;
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
