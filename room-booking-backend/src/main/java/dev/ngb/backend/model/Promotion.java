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
 * A campaign's identity and its budget.
 *
 * <p>A promotion is somebody's money, so who owns it is recorded explicitly: a host promotion must
 * name the host account funding it, and a platform promotion must not attribute itself to a host who
 * never agreed to pay.</p>
 *
 * <p>The terms live in {@link PromotionVersion} rather than here, so tightening eligibility tomorrow
 * cannot retroactively invalidate a discount a guest has already received.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("promotions")
public class Promotion {

    /** Primary key of the promotion. */
    @Id
    private @Nullable UUID id;
    /** Market it runs in; absent for a promotion that is not market-bound. */
    private @Nullable String marketCode;
    /** Whether the platform or a host owns it. */
    private PromotionOwnerType ownerType;
    /** Host account funding it; required for a host promotion. */
    private @Nullable UUID ownerAccountHolderId;
    /** Code a guest enters, when the promotion is claimed rather than assigned. */
    private @Nullable String promotionCode;
    /** Human-readable name for whoever manages the campaign. */
    private String displayName;
    /** Why it exists, which is how its success should be judged. */
    private PromotionPurpose purpose;
    /** Whether it can currently be redeemed. */
    private PromotionStatus status;
    /** Total the campaign may spend, in minor units; absent means uncapped. */
    private @Nullable Long budgetTotalMinor;
    /** ISO 4217 currency of that budget; present exactly when the budget is. */
    private @Nullable String budgetCurrency;
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
