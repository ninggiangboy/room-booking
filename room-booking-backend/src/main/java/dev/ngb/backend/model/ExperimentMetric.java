package dev.ngb.backend.model;

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
 * One metric declared on one epoch, with the role it plays.
 *
 * <p>An epoch cannot leave draft without a primary metric and a guardrail, so a conversion gain
 * bought with cancellations or host earnings is visible in the same analysis that claims it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_metrics")
public class ExperimentMetric {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment epoch this row belongs to. */
    private UUID experimentEpochId;
    /** The governed metric version being measured. */
    private UUID metricDefinitionId;
    /** What part it plays in the decision. */
    private MetricRole metricRole;
    /** Which way it is expected to move, stated before launch. */
    private ExpectedDirection expectedDirection;
    /** How far it may move before the guardrail is breached. */
    private @Nullable BigDecimal guardrailThreshold;
    /** What happens when it is. */
    private @Nullable GuardrailBreachAction guardrailBreachAction;
    /** Whether a result on this metric is confirmatory rather than exploratory. */
    private boolean confirmatory;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
