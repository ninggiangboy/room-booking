package dev.ngb.backend.admin.internal.repository.role;

import dev.ngb.backend.admin.internal.model.role.OperatorRoleDefinition;
import dev.ngb.backend.platform.GovernedRegistryStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.admin.internal.model.role.OperatorRoleDefinition;
import dev.ngb.backend.platform.GovernedRegistryStatus;


/**
 * Reads the registry of what operator roles mean.
 *
 * <p>An assignment names a role version, so this is where the question "what could somebody with
 * this role do last March" is answered. A reader that cannot find an active definition has to
 * refuse rather than grant an authority whose bounds it does not know.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operator_role_definitions}.</p>
 */
public interface OperatorRoleDefinitionRepository extends ListCrudRepository<OperatorRoleDefinition, UUID> {

    /**
     * Finds one exact version of a role.
     *
     * @param roleKey the role
     * @param roleVersion which version of it
     * @return the definition, when it is registered
     */
    Optional<OperatorRoleDefinition> findByRoleKeyAndRoleVersion(String roleKey,
            int roleVersion);

    /**
     * Lists every version of one role, newest first.
     *
     * @param roleKey the role
     * @return possibly empty list, most recently registered first
     */
    List<OperatorRoleDefinition> findByRoleKeyOrderByRoleVersionDesc(String roleKey);

    /**
     * Lists the roles in one lifecycle state.
     *
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<OperatorRoleDefinition> findByStatus(GovernedRegistryStatus status);

    /**
     * Finds the role version an assignment should be written against: the active one, or the
     * newest of them if several versions were somehow left active.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_definitions
     * WHERE role_key = :roleKey AND status = 'ACTIVE'
     * ORDER BY role_version DESC
     * LIMIT 1
     * }</pre>
     *
     * @param roleKey the role
     * @return the grantable version, when one is active
     */
    @Query("""
            SELECT * FROM operator_role_definitions
            WHERE role_key = :roleKey AND status = 'ACTIVE'
            ORDER BY role_version DESC
            LIMIT 1
            """)
    Optional<OperatorRoleDefinition> findGrantable(@Param("roleKey") String roleKey);

    /**
     * Lists the roles that may be taken under emergency access, which is the list a reviewer of
     * the break-glass configuration has to read.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_definitions
     * WHERE break_glass_eligible AND status = 'ACTIVE'
     * ORDER BY authority_class, role_key
     * }</pre>
     *
     * @return possibly empty list, by authority class then role key
     */
    @Query("""
            SELECT * FROM operator_role_definitions
            WHERE break_glass_eligible AND status = 'ACTIVE'
            ORDER BY authority_class, role_key
            """)
    List<OperatorRoleDefinition> findBreakGlassEligible();

    /**
     * Lists the roles reaching personal or restricted data, which is the list a privacy review
     * of operator access has to read.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_definitions
     * WHERE data_sensitivity IN ('PERSONAL', 'RESTRICTED') AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY data_sensitivity DESC, role_key
     * }</pre>
     *
     * @return possibly empty list, most sensitive first
     */
    @Query("""
            SELECT * FROM operator_role_definitions
            WHERE data_sensitivity IN ('PERSONAL', 'RESTRICTED') AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY data_sensitivity DESC, role_key
            """)
    List<OperatorRoleDefinition> findSensitive();
}
