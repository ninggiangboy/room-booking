package dev.ngb.backend.admin.internal.repository.configuration;

import dev.ngb.backend.admin.internal.model.configuration.ConfigurationVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a setting was actually set to, and when.
 *
 * <p>Append-only apart from being closed by its successor. The incident review reads the value that
 * was live during the incident from here, which is why nothing is allowed to rewrite it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code configuration_versions}.</p>
 */
public interface ConfigurationVersionRepository extends ListCrudRepository<ConfigurationVersion, UUID> {

    /**
     * Finds the value in force for one setting at one instant, which is the resolver lookup.
     *
     * <pre>{@code
     * SELECT * FROM configuration_versions
     * WHERE configuration_setting_id = :configurationSettingId
     *   AND effective_from <= :asOf
     *   AND (effective_until IS NULL OR effective_until > :asOf)
     * }</pre>
     *
     * @param configurationSettingId the setting
     * @param asOf the instant to read at
     * @return the value in force, when one is
     */
    @Query("""
            SELECT * FROM configuration_versions
            WHERE configuration_setting_id = :configurationSettingId
              AND effective_from <= :asOf
              AND (effective_until IS NULL OR effective_until > :asOf)
            """)
    Optional<ConfigurationVersion> findInForce(
            @Param("configurationSettingId") UUID configurationSettingId,
            @Param("asOf") Instant asOf);

    /**
     * Lists the whole history of one setting, newest first.
     *
     * @param configurationSettingId the setting
     * @return possibly empty list, newest first
     */
    List<ConfigurationVersion> findByConfigurationSettingIdOrderByVersionNumberDesc(
            UUID configurationSettingId);

    /**
     * Lists the values applied under one change request, which is how a rollout is reconciled with
     * what it actually changed.
     *
     * @param changeRequestId the request
     * @return possibly empty list
     */
    List<ConfigurationVersion> findByChangeRequestId(UUID changeRequestId);

    /**
     * Lists the values that were live across a window, which is what a post-incident timeline is
     * assembled from.
     *
     * <pre>{@code
     * SELECT * FROM configuration_versions
     * WHERE configuration_setting_id = :configurationSettingId
     *   AND effective_from < :to
     *   AND (effective_until IS NULL OR effective_until > :from)
     * ORDER BY effective_from
     * }</pre>
     *
     * @param configurationSettingId the setting
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM configuration_versions
            WHERE configuration_setting_id = :configurationSettingId
              AND effective_from < :to
              AND (effective_until IS NULL OR effective_until > :from)
            ORDER BY effective_from
            """)
    List<ConfigurationVersion> findLiveBetween(
            @Param("configurationSettingId") UUID configurationSettingId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Lists the values that were set without a request behind them, which is the list somebody has
     * to be able to justify rather than discover.
     *
     * <pre>{@code
     * SELECT * FROM configuration_versions
     * WHERE change_request_id IS NULL
     * ORDER BY applied_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM configuration_versions
            WHERE change_request_id IS NULL
            ORDER BY applied_at DESC
            """)
    List<ConfigurationVersion> findWithoutRequest();
}
