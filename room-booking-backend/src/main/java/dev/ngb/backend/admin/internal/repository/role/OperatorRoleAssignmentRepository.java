package dev.ngb.backend.admin.internal.repository.role;

import dev.ngb.backend.admin.internal.model.role.OperatorRoleAssignment;
import dev.ngb.backend.admin.internal.model.role.RoleAssignmentState;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who holds which operator authority, and until when.
 *
 * <p>This is the administrative record; the capability grant it points at is the enforcement.
 * Access reviews, recertification queues and separation-of-duties checks all read from here.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operator_role_assignments}.</p>
 */
public interface OperatorRoleAssignmentRepository extends ListCrudRepository<OperatorRoleAssignment, UUID> {

    /**
     * Lists what one operator holds in one state.
     *
     * @param operatorId the operator
     * @param assignmentState whether the authority is live
     * @return possibly empty list
     */
    List<OperatorRoleAssignment> findByOperatorIdAndAssignmentState(UUID operatorId,
            RoleAssignmentState assignmentState);

    /**
     * Finds the live assignment of one role to one operator in one market, which is the row the
     * separation-of-duties check and the console both ask for.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_assignments
     * WHERE operator_id = :operatorId AND role_key = :roleKey
     *   AND market_code IS NOT DISTINCT FROM :marketCode
     *   AND assignment_state = 'ACTIVE'
     * }</pre>
     *
     * @param operatorId the operator
     * @param roleKey the role
     * @param marketCode the market, or null for a global role
     * @return the live assignment, when there is one
     */
    @Query("""
            SELECT * FROM operator_role_assignments
            WHERE operator_id = :operatorId AND role_key = :roleKey
              AND market_code IS NOT DISTINCT FROM :marketCode
              AND assignment_state = 'ACTIVE'
            """)
    Optional<OperatorRoleAssignment> findLive(@Param("operatorId") UUID operatorId,
            @Param("roleKey") String roleKey, @Param("marketCode") @Nullable String marketCode);

    /**
     * Lists the live assignments whose recertification is due, which is the access-review queue.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_assignments
     * WHERE assignment_state = 'ACTIVE' AND recertification_due_at <= :asOf
     * ORDER BY recertification_due_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM operator_role_assignments
            WHERE assignment_state = 'ACTIVE' AND recertification_due_at <= :asOf
            ORDER BY recertification_due_at
            """)
    List<OperatorRoleAssignment> findRecertificationDue(@Param("asOf") Instant asOf);

    /**
     * Lists the live assignments that have run past their own end date, which a sweep turns into
     * expiries. An authority that outlives its expiry is a standing one nobody decided on.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_assignments
     * WHERE assignment_state = 'ACTIVE' AND effective_until <= :asOf
     * ORDER BY effective_until
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, earliest expiry first
     */
    @Query("""
            SELECT * FROM operator_role_assignments
            WHERE assignment_state = 'ACTIVE' AND effective_until <= :asOf
            ORDER BY effective_until
            """)
    List<OperatorRoleAssignment> findLapsed(@Param("asOf") Instant asOf);

    /**
     * Lists everybody holding one role right now, which is the answer to "who can do this".
     *
     * <pre>{@code
     * SELECT * FROM operator_role_assignments
     * WHERE role_key = :roleKey AND assignment_state = 'ACTIVE'
     *   AND effective_from <= :asOf AND effective_until > :asOf
     * ORDER BY effective_until
     * }</pre>
     *
     * @param roleKey the role
     * @param asOf the instant to measure against
     * @return possibly empty list, soonest to expire first
     */
    @Query("""
            SELECT * FROM operator_role_assignments
            WHERE role_key = :roleKey AND assignment_state = 'ACTIVE'
              AND effective_from <= :asOf AND effective_until > :asOf
            ORDER BY effective_until
            """)
    List<OperatorRoleAssignment> findHolders(@Param("roleKey") String roleKey,
            @Param("asOf") Instant asOf);
}
