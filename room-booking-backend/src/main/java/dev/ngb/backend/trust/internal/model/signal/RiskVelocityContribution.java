package dev.ngb.backend.trust.internal.model.signal;

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
 * One event that moved a velocity counter.
 *
 * <p>Unique by source event, so a redelivery increments nothing and a counter can be rebuilt from its
 * own history rather than trusted. Append-only.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_velocity_contributions")
public class RiskVelocityContribution {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The counter moved. */
    private UUID riskVelocityCounterId;
    /** The event that moved it; unique within the counter. */
    private String sourceEventId;
    /** The observation behind it, where there is one. */
    private @Nullable UUID riskSignalId;
    /** How much it added to the count. */
    private long deltaCount;
    /** How much it added to the amount. */
    private long deltaAmountMinor;
    /** When the underlying thing happened. */
    private Instant eventTime;
    /** Whether it arrived after its window had passed. */
    private boolean lateArrival;
    /** When the counter was updated. */
    private Instant appliedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
