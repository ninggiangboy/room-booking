package dev.ngb.backend.repository;

import dev.ngb.backend.model.ConfigurationSchema;
import dev.ngb.backend.model.GovernedRegistryStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what each configurable thing is and how it may be changed.
 *
 * <p>Every validation, approval requirement and scope rule a change request is held to is read
 * from here, which is why a change targeting a schema that is still a draft has nothing to be
 * validated against and is refused.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code configuration_schemas}.</p>
 */
public interface ConfigurationSchemaRepository extends ListCrudRepository<ConfigurationSchema, UUID> {

    /**
     * Finds one exact version of a schema.
     *
     * @param schemaKey the configurable thing
     * @param schemaVersion which version of its schema
     * @return the schema, when it is registered
     */
    Optional<ConfigurationSchema> findBySchemaKeyAndSchemaVersion(String schemaKey,
            int schemaVersion);

    /**
     * Lists every version of one schema, newest first.
     *
     * @param schemaKey the configurable thing
     * @return possibly empty list, newest version first
     */
    List<ConfigurationSchema> findBySchemaKeyOrderBySchemaVersionDesc(String schemaKey);

    /**
     * Lists what one domain owns, which is how a team finds the settings it is accountable for.
     *
     * @param owningDomain the domain
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<ConfigurationSchema> findByOwningDomainAndStatus(String owningDomain,
            GovernedRegistryStatus status);

    /**
     * Finds the schema a new setting should be created against.
     *
     * <pre>{@code
     * SELECT * FROM configuration_schemas
     * WHERE schema_key = :schemaKey AND status = 'ACTIVE'
     * ORDER BY schema_version DESC
     * LIMIT 1
     * }</pre>
     *
     * @param schemaKey the configurable thing
     * @return the usable version, when one is active
     */
    @Query("""
            SELECT * FROM configuration_schemas
            WHERE schema_key = :schemaKey AND status = 'ACTIVE'
            ORDER BY schema_version DESC
            LIMIT 1
            """)
    Optional<ConfigurationSchema> findUsable(@Param("schemaKey") String schemaKey);

    /**
     * Lists the schemas whose values reach money, identity, safety or a regulator, which is the
     * set a change-control review has to look at first.
     *
     * <pre>{@code
     * SELECT * FROM configuration_schemas
     * WHERE impact_class IN ('FINANCIAL', 'PRICING', 'IDENTITY', 'SAFETY', 'REGULATORY')
     *   AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY impact_class, schema_key
     * }</pre>
     *
     * @return possibly empty list, by impact class then key
     */
    @Query("""
            SELECT * FROM configuration_schemas
            WHERE impact_class IN ('FINANCIAL', 'PRICING', 'IDENTITY', 'SAFETY', 'REGULATORY')
              AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY impact_class, schema_key
            """)
    List<ConfigurationSchema> findConsequential();

    /**
     * Lists the schemas that cannot be rolled back, which is what somebody planning a change window
     * needs to know before the window rather than during the incident.
     *
     * <pre>{@code
     * SELECT * FROM configuration_schemas
     * WHERE NOT rollback_supported AND status = 'ACTIVE'
     * ORDER BY schema_key
     * }</pre>
     *
     * @return possibly empty list, by key
     */
    @Query("""
            SELECT * FROM configuration_schemas
            WHERE NOT rollback_supported AND status = 'ACTIVE'
            ORDER BY schema_key
            """)
    List<ConfigurationSchema> findIrreversible();
}
