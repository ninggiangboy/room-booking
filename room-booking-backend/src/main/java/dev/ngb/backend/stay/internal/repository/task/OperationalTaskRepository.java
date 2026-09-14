package dev.ngb.backend.stay.internal.repository.task;

import dev.ngb.backend.stay.internal.model.task.OperationalTask;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.stay.internal.model.task.OperationalTask;


/**
 * Reads preparation work.
 *
 * <p>Two queues matter: what one person owes today, and what is still open against a cutoff. The
 * second is what turns into a readiness exception.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operational_tasks}.</p>
 */
public interface OperationalTaskRepository extends ListCrudRepository<OperationalTask, UUID> {

    /**
     * Lists what one assignee still owes, soonest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM operational_tasks
     * WHERE assignee_account_holder_id = :assigneeAccountHolderId
     *   AND status IN ('SCHEDULED', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'REOPENED')
     * ORDER BY due_at
     * }</pre>
     *
     * <p>Matches {@code idx_operational_tasks_assignee}. Least privilege is a property of the service
     * that serves this list, not of the query: a cleaner sees the task, not the booking.</p>
     *
     * @param assigneeAccountHolderId person responsible
     * @return possibly empty list, most urgent first
     */
    @Query("""
            SELECT *
            FROM operational_tasks
            WHERE assignee_account_holder_id = :assigneeAccountHolderId
              AND status IN ('SCHEDULED', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'REOPENED')
            ORDER BY due_at
            """)
    List<OperationalTask> findAssignedWork(
            @Param("assigneeAccountHolderId") UUID assigneeAccountHolderId);

    /**
     * Lists critical work still open past its deadline.
     *
     * <pre>{@code
     * SELECT *
     * FROM operational_tasks
     * WHERE is_critical
     *   AND status NOT IN ('COMPLETED', 'CANCELLED')
     *   AND due_at <= :at
     * ORDER BY due_at
     * }</pre>
     *
     * <p>The readiness exception queue. The instant is bound by the caller because an index predicate
     * may not read the clock.</p>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM operational_tasks
            WHERE is_critical
              AND status NOT IN ('COMPLETED', 'CANCELLED')
              AND due_at <= :at
            ORDER BY due_at
            """)
    List<OperationalTask> findOverdueCritical(@Param("at") Instant at);

    /**
     * Lists the work attached to one stay.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ?}.</p>
     *
     * @param operationalStayId stay
     * @return possibly empty list
     */
    List<OperationalTask> findByOperationalStayId(UUID operationalStayId);

    /**
     * Lists the work planned at a property on one civil date.
     *
     * <p>Spring derives {@code WHERE property_id = ? AND service_date = ?}, matching
     * {@code idx_operational_tasks_property}. Turnover scheduling reads this to find collisions.</p>
     *
     * @param propertyId property
     * @param serviceDate civil date in the property's own zone
     * @return possibly empty list
     */
    List<OperationalTask> findByPropertyIdAndServiceDate(UUID propertyId, LocalDate serviceDate);

    /**
     * Locks one task for a transition.
     *
     * <pre>{@code
     * SELECT * FROM operational_tasks WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Completion writes under this lock, because the trigger that
     * checks the evidence reads another table.</p>
     *
     * @param id task to lock
     * @return the locked task, when it exists
     */
    @Query("SELECT * FROM operational_tasks WHERE id = :id FOR UPDATE")
    Optional<OperationalTask> findByIdForUpdate(@Param("id") UUID id);
}
