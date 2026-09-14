package dev.ngb.backend.stay.internal.model.incident;

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
import dev.ngb.backend.platform.ExceptionSeverity;
import dev.ngb.backend.booking.types.BookingActorType;

/**
 * Operations asking another domain to do something, and that domain's answer.
 *
 * <p>This table is the whole boundary. There is no path from an incident to a refund, a block or a
 * payout hold except a row here and a decision reference coming back; operations never writes another
 * domain's tables.</p>
 *
 * <p>One incident, one domain, one action, one key is a unique constraint, and a trigger freezes those
 * columns and the request hash so the duplicate the key refuses cannot be achieved by editing an
 * existing row instead.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("remedy_requests")
public class RemedyRequest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Incident the ask arises from. */
    private UUID incidentId;
    /** Domain being asked. */
    private RemedyTargetDomain targetDomain;
    /** What is being asked for. */
    private RemedyActionType actionType;
    /** Key making the ask repeatable without duplicating it. */
    private String idempotencyKey;
    /** Hash of the canonical request. */
    private String requestHash;
    /** Why it is being asked. */
    private String reasonCode;
    /** How urgent the ask is. */
    private ExceptionSeverity urgency;
    /** Policy that authorized it. */
    private @Nullable String policyVersion;
    /** Evidence cited in support. */
    private UUID[] evidenceLinkIds;
    /** What kind of actor asked. */
    private BookingActorType requestedByActorType;
    /** Which actor asked. */
    private @Nullable UUID requestedByActorId;
    /** When it was asked. */
    private Instant requestedAt;
    /** Where the ask stands. */
    private RemedyRequestState state;
    /** That domain's own reference for its decision. */
    private @Nullable String targetDecisionReference;
    /** That domain's decision identity. */
    private @Nullable UUID targetDecisionId;
    /** Version of that decision. */
    private @Nullable Long targetDecisionVersion;
    /** Why it was refused. */
    private @Nullable String rejectionReason;
    /** Why it could not be delivered or processed. */
    private @Nullable String failureReason;
    /** When the answer arrived. */
    private @Nullable Instant respondedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether the owning domain has answered.
     *
     * @return true once a terminal answer is recorded
     */
    public boolean isAnswered() {
        return state == RemedyRequestState.ACCEPTED || state == RemedyRequestState.REJECTED
                || state == RemedyRequestState.COMPLETED || state == RemedyRequestState.FAILED;
    }

    /**
     * Whether the incident is still waiting on this ask.
     *
     * @return true while requested, dispatched or pending
     */
    public boolean isOutstanding() {
        return state == RemedyRequestState.REQUESTED || state == RemedyRequestState.DISPATCHED
                || state == RemedyRequestState.PENDING;
    }
}
