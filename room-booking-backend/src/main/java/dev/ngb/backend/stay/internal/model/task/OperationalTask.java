package dev.ngb.backend.stay.internal.model.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * A unit of preparation work with a window, an owner and a standard of proof.
 *
 * <p>Completion is an attestation plus the evidence the task asked for. A check constraint forces the
 * name and the time; a trigger forces the evidence, and refuses sign-off while a dependency is still
 * open.</p>
 *
 * <p>Assignment honours least privilege: a cleaner needs the address and the window, not the guest's
 * payment history.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operational_tasks")
public class OperationalTask {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Property the work happens at. */
    private UUID propertyId;
    /** Listing, where the task is listing-specific. */
    private @Nullable UUID listingId;
    /** Unit, where the work is unit-specific. */
    private @Nullable UUID physicalUnitId;
    /** Stay the work prepares for or follows. */
    private @Nullable UUID operationalStayId;
    /** Booking behind that stay. */
    private @Nullable UUID bookingId;
    /** Revision the window was computed from. */
    private @Nullable UUID bookingRevisionId;
    /** Kind of work. */
    private OperationalTaskType taskType;
    /** Whether missing it at cutoff makes a readiness exception. */
    private boolean isCritical;
    /** IANA zone the local window is expressed in. */
    private String propertyTimeZone;
    /** Civil date the work is due. */
    private LocalDate serviceDate;
    /** Local start of the service window. */
    private @Nullable LocalTime windowStartLocal;
    /** Local end of the service window. */
    private @Nullable LocalTime windowEndLocal;
    /** Resolved start instant. */
    private Instant windowStartInstant;
    /** Resolved end instant. */
    private Instant windowEndInstant;
    /** Instant by which it must be done. */
    private Instant dueAt;
    /** What kind of owner it has. */
    private TaskAssigneeType assigneeType;
    /** Account holder responsible, where there is one. */
    private @Nullable UUID assigneeAccountHolderId;
    /** External vendor reference, where the owner is not on the platform. */
    private @Nullable String assigneeReference;
    /** Whether completion needs evidence that passed scanning. */
    private boolean requiresEvidence;
    /** What evidence is asked for. */
    private String[] requiredEvidenceTypes;
    /** Task that must finish first. */
    private @Nullable UUID dependsOnTaskId;
    /** Where the work stands. */
    private OperationalTaskStatus status;
    /** Why it cannot proceed. */
    private @Nullable String blockedReason;
    /** When work began. */
    private @Nullable Instant startedAt;
    /** When it was attested complete. */
    private @Nullable Instant completedAt;
    /** Who attested to it. */
    private @Nullable UUID completedByAccountHolderId;
    /** What they said about it. */
    private @Nullable String completionNote;
    /** When it was cancelled. */
    private @Nullable Instant cancelledAt;
    /** Why it was cancelled. */
    private @Nullable String cancellationReason;
    /** How many times it has been reopened. */
    private int reopenCount;
    /** Why it was last reopened. */
    private @Nullable String reopenReason;
    /** Operations policy in force. */
    private @Nullable String policyVersion;
    /** Checklist template in force. */
    private @Nullable String templateVersion;
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
     * Whether this task still needs somebody to do something.
     *
     * @return true while not completed or cancelled
     */
    public boolean isOpen() {
        return status != OperationalTaskStatus.COMPLETED
                && status != OperationalTaskStatus.CANCELLED;
    }

    /**
     * Whether this task is a readiness risk at the given instant.
     *
     * <p>A critical task still open past its deadline is what turns into a readiness exception and,
     * where the property cannot be made ready, a request for an emergency block.</p>
     *
     * @param at instant to test
     * @return true when critical, still open, and overdue
     */
    public boolean isOverdueCritical(Instant at) {
        return isCritical && isOpen() && at.isAfter(dueAt);
    }
}
