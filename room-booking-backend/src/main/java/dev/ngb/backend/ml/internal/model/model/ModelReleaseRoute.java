package dev.ngb.backend.ml.internal.model.model;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;


/**
 * Where a model version becomes live for one consumer, decision scope and market.
 *
 * <p>Routing is the only thing a rollback changes; immutable history is never rolled back. One
 * active route exists per scope, and a champion/challenger split must name the experiment epoch it
 * is measured under, because an unmeasured traffic split is not a comparison. The route also owns
 * the fail-open or fail-closed decision, since the same model can be safe to skip on a search page
 * and unsafe to skip at a payout decision.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("model_release_routes")
public class ModelReleaseRoute {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The service or use case this route serves. */
    private String consumer;
    /** The decision within that consumer the route covers. */
    private String decisionScope;
    /** ISO 3166-1 alpha-2 market, absent for a route that covers all of them. */
    private @Nullable String marketCode;
    /** The version serving this scope. */
    private UUID championModelVersionId;
    /** The version taking a share of traffic against the champion. */
    private @Nullable UUID challengerModelVersionId;
    /** Fraction of traffic the challenger takes, strictly between none and all. */
    private @Nullable BigDecimal challengerShare;
    /** The epoch the split is measured under; required whenever there is a challenger. */
    private @Nullable UUID experimentEpochId;
    /** How much real traffic the route carries. */
    private RouteMode routeMode;
    /** What the consumer does when no version can answer. */
    private ModelFallbackBehaviour fallbackBehaviour;
    /** Whether the consumer proceeds without a prediction or stops. */
    private RouteFailMode failMode;
    /** Total time the consumer allows before falling back. */
    private int inferenceBudgetMs;
    /** Where the guardrails for this route are defined. */
    private @Nullable String guardrailReference;
    /** The version routing returns to when the route is rolled back. */
    private @Nullable UUID rollbackTargetVersionId;
    /** UTC instant the route takes effect. */
    private Instant effectiveFrom;
    /** UTC instant the route stops applying, absent while it is current. */
    private @Nullable Instant effectiveTo;
    /** Who approved live traffic; required for canary and active routes. */
    private @Nullable String approvedBy;
    /** UTC instant that approval was given. */
    private @Nullable Instant approvedAt;
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
