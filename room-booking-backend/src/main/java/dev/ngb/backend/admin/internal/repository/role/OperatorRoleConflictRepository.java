package dev.ngb.backend.admin.internal.repository.role;

import dev.ngb.backend.admin.internal.model.role.OperatorRoleConflict;
import dev.ngb.backend.admin.internal.model.role.RoleConflictBasis;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the declared pairs of roles one person may not hold at once.
 *
 * <p>The pair is stored in one order, so a conflict is one row rather than two that can disagree
 * about whether it still stands. Both lookups below therefore have to ask about each side.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operator_role_conflicts}.</p>
 */
public interface OperatorRoleConflictRepository extends ListCrudRepository<OperatorRoleConflict, UUID> {

    /**
     * Lists the conflicts still in force that name one role, whichever side of the pair it is on.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_conflicts
     * WHERE withdrawn_at IS NULL AND :roleKey IN (lower_role_key, higher_role_key)
     * ORDER BY lower_role_key, higher_role_key
     * }</pre>
     *
     * @param roleKey the role
     * @return possibly empty list, by role key pair
     */
    @Query("""
            SELECT * FROM operator_role_conflicts
            WHERE withdrawn_at IS NULL AND :roleKey IN (lower_role_key, higher_role_key)
            ORDER BY lower_role_key, higher_role_key
            """)
    List<OperatorRoleConflict> findLiveForRole(@Param("roleKey") String roleKey);

    /**
     * Finds the conflict declared between two roles, in whichever order the caller names them.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_conflicts
     * WHERE lower_role_key = LEAST(:firstRoleKey, :secondRoleKey)
     *   AND higher_role_key = GREATEST(:firstRoleKey, :secondRoleKey)
     * }</pre>
     *
     * @param firstRoleKey one of the two roles
     * @param secondRoleKey the other
     * @return the conflict, when one is declared
     */
    @Query("""
            SELECT * FROM operator_role_conflicts
            WHERE lower_role_key = LEAST(:firstRoleKey, :secondRoleKey)
              AND higher_role_key = GREATEST(:firstRoleKey, :secondRoleKey)
            """)
    Optional<OperatorRoleConflict> findForPair(@Param("firstRoleKey") String firstRoleKey,
            @Param("secondRoleKey") String secondRoleKey);

    /**
     * Lists the conflicts still in force that arise from one basis, which is how a control owner
     * reviews the set they are responsible for.
     *
     * @param conflictBasis why the roles are kept apart
     * @return possibly empty list
     */
    List<OperatorRoleConflict> findByConflictBasisAndWithdrawnAtIsNull(
            RoleConflictBasis conflictBasis);
}
