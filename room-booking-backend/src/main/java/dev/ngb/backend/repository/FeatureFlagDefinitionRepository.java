package dev.ngb.backend.repository;

import dev.ngb.backend.model.FeatureFlagDefinition;
import dev.ngb.backend.model.FeatureFlagKind;
import dev.ngb.backend.model.GovernedRegistryStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the flag registry: what each flag is, who may turn it on, and the date it is gone by.
 *
 * <p>The expiry queries below are the reason the table exists rather than a key-value store. A flag
 * nobody removed is product behaviour that never went through a product decision.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code feature_flag_definitions}.</p>
 */
public interface FeatureFlagDefinitionRepository extends ListCrudRepository<FeatureFlagDefinition, UUID> {

    /**
     * Finds one flag by key.
     *
     * @param flagKey the flag
     * @return the definition, when it is registered
     */
    Optional<FeatureFlagDefinition> findByFlagKey(String flagKey);

    /**
     * Lists the flags one domain owns.
     *
     * @param owningDomain the domain
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<FeatureFlagDefinition> findByOwningDomainAndStatus(String owningDomain,
            GovernedRegistryStatus status);

    /**
     * Lists the flags of one kind, which is how the set of kill switches is found before an
     * incident rather than during one.
     *
     * @param flagKind what the flag is for
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<FeatureFlagDefinition> findByFlagKindAndStatus(FeatureFlagKind flagKind,
            GovernedRegistryStatus status);

    /**
     * Lists the flags past the date their owner agreed to. Past it the only permitted moves are off
     * and retired, so this is a queue rather than a report.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_definitions
     * WHERE status = 'ACTIVE' AND expires_on < :asOf
     * ORDER BY expires_on
     * }</pre>
     *
     * @param asOf the date to measure against
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM feature_flag_definitions
            WHERE status = 'ACTIVE' AND expires_on < :asOf
            ORDER BY expires_on
            """)
    List<FeatureFlagDefinition> findExpired(@Param("asOf") LocalDate asOf);

    /**
     * Lists the flags due to expire soon, so their owners are asked before the date rather than
     * after it.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_definitions
     * WHERE status = 'ACTIVE' AND expires_on >= :from AND expires_on < :to
     * ORDER BY expires_on
     * }</pre>
     *
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, soonest first
     */
    @Query("""
            SELECT * FROM feature_flag_definitions
            WHERE status = 'ACTIVE' AND expires_on >= :from AND expires_on < :to
            ORDER BY expires_on
            """)
    List<FeatureFlagDefinition> findExpiringBetween(@Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Lists the flags that have been extended more than once, which is the honest way to find the
     * rollout mechanisms that quietly became permanent.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_definitions
     * WHERE extension_count > 1 AND status = 'ACTIVE'
     * ORDER BY extension_count DESC, expires_on
     * }</pre>
     *
     * @return possibly empty list, most extended first
     */
    @Query("""
            SELECT * FROM feature_flag_definitions
            WHERE extension_count > 1 AND status = 'ACTIVE'
            ORDER BY extension_count DESC, expires_on
            """)
    List<FeatureFlagDefinition> findRepeatedlyExtended();
}
