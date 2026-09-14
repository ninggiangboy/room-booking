package dev.ngb.backend.hostops.internal.repository.forecast;

import dev.ngb.backend.hostops.internal.model.forecast.PromotionSuggestion;
import dev.ngb.backend.hostops.internal.model.forecast.SuggestionDecisionState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.forecast.PromotionSuggestion;
import dev.ngb.backend.hostops.internal.model.forecast.SuggestionDecisionState;


/**
 * Reads the promotions the platform thinks are worth running, with what they would add and cost.
 *
 * <p>A suggestion is frozen once decided, so a reader can compare what the host agreed to against
 * what the promotion actually did without wondering whether the estimate moved in between.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code promotion_suggestions}.</p>
 */
public interface PromotionSuggestionRepository extends ListCrudRepository<PromotionSuggestion, UUID> {

    /**
     * Lists the suggestions still open for one accommodation type, soonest to expire first, which
     * is the order a host screen shows them in.
     *
     * <pre>{@code
     * SELECT * FROM promotion_suggestions
     * WHERE accommodation_type_id = :accommodationTypeId AND decision_state = 'PENDING'
     * ORDER BY expires_at
     * }</pre>
     *
     * @param accommodationTypeId the accommodation type
     * @return possibly empty list, soonest to expire first
     */
    @Query("""
            SELECT * FROM promotion_suggestions
            WHERE accommodation_type_id = :accommodationTypeId AND decision_state = 'PENDING'
            ORDER BY expires_at
            """)
    List<PromotionSuggestion> findOpen(
            @Param("accommodationTypeId") UUID accommodationTypeId);

    /**
     * Lists the suggestions for one accommodation type in one decision state.
     *
     * @param accommodationTypeId the accommodation type
     * @param decisionState what the host did
     * @return possibly empty list
     */
    List<PromotionSuggestion> findByAccommodationTypeIdAndDecisionState(UUID accommodationTypeId,
            SuggestionDecisionState decisionState);

    /**
     * Finds the suggestion a promotion came from.
     *
     * @param acceptedPromotionId the promotion the host created
     * @return the suggestion, when the promotion came from one
     */
    Optional<PromotionSuggestion> findByAcceptedPromotionId(UUID acceptedPromotionId);

    /**
     * Lists the suggestions that lapsed without an answer, which is what a review of whether the
     * suggestions are worth making at all has to read.
     *
     * <pre>{@code
     * SELECT * FROM promotion_suggestions
     * WHERE decision_state = 'EXPIRED' AND generated_at >= :since
     * ORDER BY generated_at DESC
     * }</pre>
     *
     * @param since earliest generation instant to include
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM promotion_suggestions
            WHERE decision_state = 'EXPIRED' AND generated_at >= :since
            ORDER BY generated_at DESC
            """)
    List<PromotionSuggestion> findLapsed(@Param("since") Instant since);
}
