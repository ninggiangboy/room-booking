package dev.ngb.backend.repository;

import dev.ngb.backend.model.StoredValueHold;
import dev.ngb.backend.model.StoredValueHoldState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the credit set aside against open quotes.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code stored_value_holds}.</p>
 */
public interface StoredValueHoldRepository extends ListCrudRepository<StoredValueHold, UUID> {

    /**
     * Finds the hold against one quote, of which there is at most one.
     *
     * @param quoteId the quote
     * @return the hold, when the quote holds credit
     */
    Optional<StoredValueHold> findByQuoteId(UUID quoteId);

    /**
     * Lists the holds against one balance in one state.
     *
     * @param storedValueAccountId the balance
     * @param state where the holds stand
     * @return possibly empty list
     */
    List<StoredValueHold> findByStoredValueAccountIdAndState(UUID storedValueAccountId,
            StoredValueHoldState state);

    /**
     * Lists the holds that have lapsed, which is the set the release sweep takes: credit behind an
     * abandoned checkout has to come back.
     *
     * <pre>{@code
     * SELECT * FROM stored_value_holds
     * WHERE state = 'HELD' AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest lapsed first
     */
    @Query("""
            SELECT * FROM stored_value_holds
            WHERE state = 'HELD' AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<StoredValueHold> findLapsed(@Param("at") Instant at);
}
