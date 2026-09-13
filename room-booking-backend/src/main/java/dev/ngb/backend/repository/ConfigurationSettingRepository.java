package dev.ngb.backend.repository;

import dev.ngb.backend.model.ConfigurationSetting;
import dev.ngb.backend.model.ConfigurationScopeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the addressable configuration settings and the order they resolve in.
 *
 * <p>Resolution is data rather than code: the candidates for a lookup and the priority that picks
 * between them are both read from here, so the question "why did this request see that value" has
 * an answer that does not depend on reading the resolver.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code configuration_settings}.</p>
 */
public interface ConfigurationSettingRepository extends ListCrudRepository<ConfigurationSetting, UUID> {

    /**
     * Lists the settings under one schema key, most specific first.
     *
     * @param schemaKey the configurable thing
     * @return possibly empty list, highest resolution priority first
     */
    List<ConfigurationSetting> findBySchemaKeyOrderByResolutionPriorityDesc(String schemaKey);

    /**
     * Finds the setting at one exact scope, which is the row a change request targets.
     *
     * <pre>{@code
     * SELECT * FROM configuration_settings
     * WHERE configuration_schema_id = :configurationSchemaId AND scope_type = :scopeType
     *   AND scope_id IS NOT DISTINCT FROM :scopeId
     *   AND market_code IS NOT DISTINCT FROM :marketCode
     * }</pre>
     *
     * @param configurationSchemaId the schema version
     * @param scopeType the scope kind
     * @param scopeId the scoped resource, or null
     * @param marketCode the market, or null
     * @return the setting, when one exists at that scope
     */
    @Query("""
            SELECT * FROM configuration_settings
            WHERE configuration_schema_id = :configurationSchemaId AND scope_type = :scopeType
              AND scope_id IS NOT DISTINCT FROM :scopeId
              AND market_code IS NOT DISTINCT FROM :marketCode
            """)
    Optional<ConfigurationSetting> findAtScope(
            @Param("configurationSchemaId") UUID configurationSchemaId,
            @Param("scopeType") ConfigurationScopeType scopeType,
            @Param("scopeId") @Nullable UUID scopeId,
            @Param("marketCode") @Nullable String marketCode);

    /**
     * Lists the settings that could apply to one market and one scoped resource, in the order the
     * resolver considers them. The caller takes the first whose value is in force.
     *
     * <pre>{@code
     * SELECT * FROM configuration_settings
     * WHERE schema_key = :schemaKey AND setting_state = 'ACTIVE'
     *   AND (scope_type = 'GLOBAL'
     *        OR (scope_type = 'MARKET' AND market_code = :marketCode)
     *        OR (scope_id IS NOT NULL AND scope_id = :scopeId))
     * ORDER BY resolution_priority DESC
     * }</pre>
     *
     * @param schemaKey the configurable thing
     * @param marketCode the market, or null
     * @param scopeId the scoped resource, or null
     * @return possibly empty list, most specific first
     */
    @Query("""
            SELECT * FROM configuration_settings
            WHERE schema_key = :schemaKey AND setting_state = 'ACTIVE'
              AND (scope_type = 'GLOBAL'
                   OR (scope_type = 'MARKET' AND market_code = :marketCode)
                   OR (scope_id IS NOT NULL AND scope_id = :scopeId))
            ORDER BY resolution_priority DESC
            """)
    List<ConfigurationSetting> findResolutionCandidates(@Param("schemaKey") String schemaKey,
            @Param("marketCode") @Nullable String marketCode,
            @Param("scopeId") @Nullable UUID scopeId);

    /**
     * Lists the settings one team is accountable for.
     *
     * @param ownerReference the owning team
     * @return possibly empty list
     */
    List<ConfigurationSetting> findByOwnerReference(String ownerReference);
}
