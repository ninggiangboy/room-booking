package dev.ngb.backend.admin.internal.repository.command;

import dev.ngb.backend.admin.internal.model.command.OperationalCommandDefinition;
import dev.ngb.backend.platform.GovernedRegistryStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the catalogue of what an operator may ask the marketplace to do.
 *
 * <p>The ceiling, the approvals and the permission an execution is held to are all read from here,
 * which is why a retired command is not an available action however familiar it was last week.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operational_command_definitions}.</p>
 */
public interface OperationalCommandDefinitionRepository extends ListCrudRepository<OperationalCommandDefinition, UUID> {

    /**
     * Finds one exact version of a command.
     *
     * @param commandKey the command
     * @param commandVersion which version of it
     * @return the definition, when it is registered
     */
    Optional<OperationalCommandDefinition> findByCommandKeyAndCommandVersion(String commandKey,
            int commandVersion);

    /**
     * Finds the command version an execution should be written against.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_definitions
     * WHERE command_key = :commandKey AND status = 'ACTIVE'
     * ORDER BY command_version DESC
     * LIMIT 1
     * }</pre>
     *
     * @param commandKey the command
     * @return the issuable version, when one is active
     */
    @Query("""
            SELECT * FROM operational_command_definitions
            WHERE command_key = :commandKey AND status = 'ACTIVE'
            ORDER BY command_version DESC
            LIMIT 1
            """)
    Optional<OperationalCommandDefinition> findIssuable(@Param("commandKey") String commandKey);

    /**
     * Lists what one domain will accept, which is how a console builds its menu.
     *
     * @param owningDomain the domain
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<OperationalCommandDefinition> findByOwningDomainAndStatus(String owningDomain,
            GovernedRegistryStatus status);

    /**
     * Lists the commands one permission opens, which is what an operator can actually do with the
     * authority they hold.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_definitions
     * WHERE required_permission_key = ANY (:permissionKeys) AND status = 'ACTIVE'
     * ORDER BY owning_domain, command_key
     * }</pre>
     *
     * @param permissionKeys the permissions the operator holds
     * @return possibly empty list, by domain then command key
     */
    @Query("""
            SELECT * FROM operational_command_definitions
            WHERE required_permission_key = ANY (:permissionKeys) AND status = 'ACTIVE'
            ORDER BY owning_domain, command_key
            """)
    List<OperationalCommandDefinition> findAvailable(
            @Param("permissionKeys") String[] permissionKeys);

    /**
     * Lists the commands that move money, which is the set a financial-control review looks at and
     * the set whose ceilings somebody has to agree are still the right ones.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_definitions
     * WHERE monetary AND status = 'ACTIVE'
     * ORDER BY maximum_amount_minor DESC
     * }</pre>
     *
     * @return possibly empty list, largest ceiling first
     */
    @Query("""
            SELECT * FROM operational_command_definitions
            WHERE monetary AND status = 'ACTIVE'
            ORDER BY maximum_amount_minor DESC
            """)
    List<OperationalCommandDefinition> findMonetary();

    /**
     * Lists the commands that cannot be undone, which is what somebody needs to know before issuing
     * one rather than after.
     *
     * <pre>{@code
     * SELECT * FROM operational_command_definitions
     * WHERE NOT reversible AND status = 'ACTIVE'
     * ORDER BY owning_domain, command_key
     * }</pre>
     *
     * @return possibly empty list, by domain then command key
     */
    @Query("""
            SELECT * FROM operational_command_definitions
            WHERE NOT reversible AND status = 'ACTIVE'
            ORDER BY owning_domain, command_key
            """)
    List<OperationalCommandDefinition> findIrreversible();
}
