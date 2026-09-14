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
import org.springframework.data.relational.core.mapping.Table;


/**
 * One command against a model version or its route, and whether it was applied.
 *
 * <p>Approving or promoting is a two-person decision: the person who asks is not the person who
 * grants. Monitoring automation may pause or roll back the instant a threshold breaks, which is the
 * whole point of a kill switch, and may never promote -- automation that can ship is a release
 * process with no human in it, discovered later by whoever is on call.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("model_actions")
public class ModelAction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The version the command is about. */
    private UUID modelVersionId;
    /** The route being changed, required for a promotion. */
    private @Nullable UUID modelReleaseRouteId;
    /** What the command asks for. */
    private ModelActionType actionType;
    /** The optimistic-lock version the caller believed it was acting on. */
    private long expectedVersion;
    /** Whether a person or monitoring automation issued it. */
    private ModelActorKind actorKind;
    /** Who issued it. */
    private String actorReference;
    /** Who granted it; required for approve and promote, never the requester. */
    private @Nullable String approverReference;
    /** Exactly what authority the command grants or withdraws. */
    private String actionScope;
    /** Share of traffic being granted, for a promotion that changes one. */
    private @Nullable BigDecimal trafficShare;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** Why, in prose, for whoever reads the audit afterwards. */
    private String reasonDetail;
    /** The evaluation that triggered it; required for an automated command. */
    private @Nullable UUID triggeringEvaluationId;
    /** UTC instant the command was issued. */
    private Instant requestedAt;
    /** Whether the command has taken effect. */
    private boolean applied;
    /** UTC instant it took effect. */
    private @Nullable Instant appliedAt;
    /** Why it could not be applied. */
    private @Nullable String failureReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
