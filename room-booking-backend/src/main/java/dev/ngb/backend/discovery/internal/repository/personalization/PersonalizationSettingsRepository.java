package dev.ngb.backend.discovery.internal.repository.personalization;

import dev.ngb.backend.discovery.internal.model.personalization.PersonalizationSettings;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a guest chose about personalization.
 *
 * <p>Every derived-profile job reads this first. Absence means the guest has expressed no preference,
 * which is not the same as having declined.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code personalization_settings}.</p>
 */
public interface PersonalizationSettingsRepository extends ListCrudRepository<PersonalizationSettings, UUID> {

    /**
     * Finds one guest's settings.
     *
     * @param accountHolderId the guest
     * @return the settings, when the guest has a row
     */
    Optional<PersonalizationSettings> findByAccountHolderId(UUID accountHolderId);

    /**
     * Lists guests who have declined behavioural profiling, for the derived-store sweep.
     *
     * <pre>{@code
     * SELECT * FROM personalization_settings
     * WHERE behavioral_profiling = false AND effective_from <= :at
     * ORDER BY effective_from
     * }</pre>
     *
     * @param at instant to resolve at
     * @return possibly empty list, oldest decision first
     */
    @Query("""
            SELECT * FROM personalization_settings
            WHERE behavioral_profiling = false AND effective_from <= :at
            ORDER BY effective_from
            """)
    List<PersonalizationSettings> findDeclined(@Param("at") Instant at);
}
