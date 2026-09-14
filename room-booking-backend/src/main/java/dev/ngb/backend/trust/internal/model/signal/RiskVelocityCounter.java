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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.platform.JsonDocument;


/**
 * A bounded count or amount over one event-time window.
 *
 * <p>Held as a row so a strong transaction limit can be enforced by locking it rather than by hoping
 * two requests do not arrive together. Distinct entities can never outnumber events, and late arrivals
 * are counted separately so a window that filled up out of order can still be explained.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_velocity_counters")
public class RiskVelocityCounter {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The velocity rule this counter serves. */
    private String ruleKey;
    /** Hex digest of the dimension values; part of the counter identity. */
    private String dimensionDigest;
    /** The dimension values themselves. */
    private JsonDocument dimensionDocument;
    /** Start of the event-time window. */
    private Instant windowStart;
    /** End of it. */
    private Instant windowEnd;
    /** Events counted in the window. */
    private long eventCount;
    /** Distinct entities seen; never more than the events. */
    private long distinctEntityCount;
    /** Money accumulated, in minor units. */
    private long amountMinor;
    /** Currency of that amount. */
    private @Nullable String currency;
    /** Count at which the rule acts. */
    private @Nullable Long thresholdCount;
    /** Amount at which the rule acts. */
    private @Nullable Long thresholdAmountMinor;
    /** How many arrived after the window had passed. */
    private long lateEventCount;
    /** Event time of the most recent contribution. */
    private @Nullable Instant lastEventTime;
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
