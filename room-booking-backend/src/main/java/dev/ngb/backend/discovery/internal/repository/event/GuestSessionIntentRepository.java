package dev.ngb.backend.discovery.internal.repository.event;

import dev.ngb.backend.discovery.internal.model.event.GuestSessionIntent;
import dev.ngb.backend.discovery.internal.model.PreferenceDimensionKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what the current session appears to be looking for.
 *
 * <p>Session intent steers the session it belongs to. Folding it into the durable profile is a
 * separate, permitted act, and the database refuses it for a guest who declined profiling.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code guest_session_intents}.</p>
 */
public interface GuestSessionIntentRepository extends ListCrudRepository<GuestSessionIntent, UUID> {

    /**
     * Lists unexpired intent for one session.
     *
     * <pre>{@code
     * SELECT * FROM guest_session_intents
     * WHERE session_pseudonym = :sessionPseudonym AND expires_at > :at
     * ORDER BY observation_count DESC
     * }</pre>
     *
     * @param sessionPseudonym analytical pseudonym for the session
     * @param at instant to resolve at
     * @return possibly empty list, most repeatedly observed first
     */
    @Query("""
            SELECT * FROM guest_session_intents
            WHERE session_pseudonym = :sessionPseudonym AND expires_at > :at
            ORDER BY observation_count DESC
            """)
    List<GuestSessionIntent> findLive(@Param("sessionPseudonym") String sessionPseudonym,
            @Param("at") Instant at);

    /**
     * Finds one dimension within one session.
     *
     * @param sessionPseudonym analytical pseudonym for the session
     * @param dimensionKind the kind
     * @param dimensionKey the dimension
     * @return the row, when this session has observed it
     */
    Optional<GuestSessionIntent> findBySessionPseudonymAndDimensionKindAndDimensionKey(
            String sessionPseudonym, PreferenceDimensionKind dimensionKind, String dimensionKey);

    /**
     * Deletes expired session intent.
     *
     * <pre>{@code
     * DELETE FROM guest_session_intents WHERE expires_at <= :at
     * }</pre>
     *
     * <p>Session intent is never kept indefinitely, so this sweep is part of honouring the promise that
     * it is session-scoped rather than a profile by another name.</p>
     *
     * @param at instant to compare against
     * @return how many rows were removed
     */
    @Query("DELETE FROM guest_session_intents WHERE expires_at <= :at")
    int deleteExpired(@Param("at") Instant at);
}
