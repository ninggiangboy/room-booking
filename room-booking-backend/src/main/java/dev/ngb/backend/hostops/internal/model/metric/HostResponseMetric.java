package dev.ngb.backend.hostops.internal.model.metric;

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
import org.springframework.data.relational.core.mapping.Table;

import dev.ngb.backend.hostops.internal.model.MetricEvidenceState;


/**
 * How responsive, accepting, reliable and compliant a host was over one period, with the counts
 * each rate was computed from.
 * <p>Ninety per cent of ten is a different fact from ninety per cent of a thousand, and a host
 * penalised by the first deserves to see which it was. Every rate here is stored beside its
 * numerator and denominator and checked against them, so the figure on the host screen and the
 * figure the platform acted on cannot be two different numbers.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_response_metrics")
public class HostResponseMetric {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The host these figures describe. */
    private UUID hostAccountHolderId;
    /** First civil day the figures cover. */
    private LocalDate periodStart;
    /** Day after the last civil day the figures cover. */
    private LocalDate periodEnd;
    /** IANA zone the civil period boundaries were computed in. */
    private String periodTimeZone;
    /** Whether there was enough activity in the period for the figures to mean anything. */
    private MetricEvidenceState evidenceState;
    /** How many guest enquiries arrived in the period. */
    private long inquiriesReceived;
    /** How many of them the host answered. */
    private long inquiriesResponded;
    /**
     * Answered enquiries over enquiries received, stored beside both counts so the two can never
     * disagree.
     */
    private @Nullable BigDecimal responseRate;
    /** Median time to first reply, in seconds. */
    private @Nullable Integer medianResponseSeconds;
    /** The reply time only a tenth of replies were slower than, in seconds. */
    private @Nullable Integer slowestDecileSeconds;
    /** How many booking requests arrived in the period. */
    private long requestsReceived;
    /** How many the host accepted. */
    private long requestsAccepted;
    /** How many the host declined. */
    private long requestsDeclined;
    /** How many the host let expire without answering. */
    private long requestsExpired;
    /** Accepted requests over requests received. */
    private @Nullable BigDecimal acceptanceRate;
    /** How many bookings were confirmed in the period. */
    private long bookingsConfirmed;
    /** How many confirmed bookings the host cancelled. */
    private long hostCancellations;
    /** Host cancellations over confirmed bookings. */
    private @Nullable BigDecimal hostCancellationRate;
    /**
     * How many of those cancellations were excused under policy and do not count against the host.
     */
    private long excusedCancellations;
    /** How many policy breaches were recorded against the host in the period. */
    private long policyBreachesRecorded;
    /** Where the host stands under the platforms hosting standards. */
    private HostComplianceState complianceState;
    /** Why the host is not in good standing, required whenever they are not. */
    private @Nullable String complianceExplanation;
    /** UTC instant up to which input data was complete when the figures were computed. */
    private Instant inputWatermark;
    /** UTC instant the figures were computed. */
    private Instant computedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
