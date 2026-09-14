package dev.ngb.backend.admin.internal.repository.role;

import dev.ngb.backend.admin.internal.model.role.OperatorRolePermission;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads what a role version actually confers.
 *
 * <p>Membership is the contract: a role version means exactly these permissions, and adding one
 * afterwards would widen every grant already made under it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operator_role_permissions}.</p>
 */
public interface OperatorRolePermissionRepository extends ListCrudRepository<OperatorRolePermission, UUID> {

    /**
     * Lists what one role version confers.
     *
     * @param roleDefinitionId the role version
     * @return possibly empty list
     */
    List<OperatorRolePermission> findByRoleDefinitionId(UUID roleDefinitionId);

    /**
     * Finds every role version conferring one permission, which is how the reverse question -- who
     * can do this -- is answered.
     *
     * @param permissionKey the permission
     * @return possibly empty list
     */
    List<OperatorRolePermission> findByPermissionKey(String permissionKey);

    /**
     * Lists the permissions of one role that need a second person at the point of use, which the
     * console has to know before it offers the action.
     *
     * <pre>{@code
     * SELECT * FROM operator_role_permissions
     * WHERE role_definition_id = :roleDefinitionId AND requires_second_approval
     * ORDER BY permission_key
     * }</pre>
     *
     * @param roleDefinitionId the role version
     * @return possibly empty list, by permission key
     */
    @Query("""
            SELECT * FROM operator_role_permissions
            WHERE role_definition_id = :roleDefinitionId AND requires_second_approval
            ORDER BY permission_key
            """)
    List<OperatorRolePermission> findSecondApprovalRequired(
            @Param("roleDefinitionId") UUID roleDefinitionId);

    /**
     * Counts what a role version confers, which is what the activation guard checks before letting
     * a role out of draft.
     *
     * <pre>{@code
     * SELECT count(*) FROM operator_role_permissions WHERE role_definition_id = :roleDefinitionId
     * }</pre>
     *
     * @param roleDefinitionId the role version
     * @return how many permissions it carries
     */
    @Query("SELECT count(*) FROM operator_role_permissions WHERE role_definition_id = :roleDefinitionId")
    long countForRole(@Param("roleDefinitionId") UUID roleDefinitionId);
}
