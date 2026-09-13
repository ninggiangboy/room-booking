package dev.ngb.backend.repository;

import dev.ngb.backend.model.EventDefinition;
import dev.ngb.backend.model.EventDefinitionStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the registered contract for an event name and schema version.
 *
 * <p>The collector resolves every arrival through this table. A name and version nobody registered
 * is quarantined rather than accepted, which is what makes an unregistered producer visible.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code event_definitions}.</p>
 */
public interface EventDefinitionRepository extends ListCrudRepository<EventDefinition, UUID> {

    /**
     * Finds the contract an arrival cites.
     *
     * @param eventName the event name
     * @param schemaVersion the schema version it claims
     * @return the contract, when it is registered
     */
    Optional<EventDefinition> findByEventNameAndSchemaVersion(String eventName,
            short schemaVersion);

    /**
     * Lists the versions of one event name that still accept arrivals.
     *
     * <pre>{@code
     * SELECT * FROM event_definitions
     * WHERE event_name = :eventName AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY schema_version DESC
     * }</pre>
     *
     * @param eventName the event name
     * @return possibly empty list, newest schema version first
     */
    @Query("""
            SELECT * FROM event_definitions
            WHERE event_name = :eventName AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY schema_version DESC
            """)
    List<EventDefinition> findAcceptable(@Param("eventName") String eventName);

    /**
     * Lists contracts in one lifecycle state, for the registry console.
     *
     * @param status the state to list
     * @return possibly empty list, by name and version
     */
    List<EventDefinition> findByStatusOrderByEventNameAscSchemaVersionAsc(
            EventDefinitionStatus status);

    /**
     * Lists deprecated contracts whose last production date has passed, for the retirement sweep.
     *
     * <pre>{@code
     * SELECT * FROM event_definitions
     * WHERE status = 'DEPRECATED' AND last_production_at <= :at
     * ORDER BY last_production_at
     * }</pre>
     *
     * <p>Retirement is still refused while a consumer stands, so this list is a starting point for the
     * conversation rather than a work queue that can be drained without asking anyone.</p>
     *
     * @param at instant to compare against
     * @return possibly empty list, longest idle first
     */
    @Query("""
            SELECT * FROM event_definitions
            WHERE status = 'DEPRECATED' AND last_production_at <= :at
            ORDER BY last_production_at
            """)
    List<EventDefinition> findRetirable(@Param("at") Instant at);
}
