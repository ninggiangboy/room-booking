package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * The stable identity of a pricing rule, separate from what it currently says.
 *
 * <p>This row survives editing; {@link PriceRuleVersion} is what pricing actually reads. The split is
 * what lets a host keep "my weekly discount" across years of revisions without rewriting what any
 * past booking was charged.</p>
 *
 * <p>The scope decides which subject column must be populated, and the database enforces the
 * agreement: a rule claiming market scope without naming a market would silently apply everywhere.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("price_rules")
public class PriceRule {

    /** Primary key of the rule. */
    @Id
    private @Nullable UUID id;
    /** Which population the rule governs. */
    private PriceRuleScope scope;
    /** Market it applies to, for a market-scoped rule. */
    private @Nullable String marketCode;
    /** Host it belongs to, for a host-scoped rule. */
    private @Nullable UUID accountHolderId;
    /** Accommodation type it applies to, for a type-scoped rule. */
    private @Nullable UUID accommodationTypeId;
    /** Rate plan it applies to, for a rate-plan-scoped rule. */
    private @Nullable UUID ratePlanId;
    /** What the rule reacts to, which fixes the facts its condition may read. */
    private PriceRuleType ruleType;
    /** Human-readable name, shown to whoever manages the rule. */
    private String displayName;
    /** Resolution order among compatible rules; lower runs first. */
    private int priority;
    /** Rules sharing a group are rivals and only one of them applies. */
    private @Nullable String compatibilityGroup;
    /** Whether the rule's identity is in use. */
    private PriceRuleStatus status;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
