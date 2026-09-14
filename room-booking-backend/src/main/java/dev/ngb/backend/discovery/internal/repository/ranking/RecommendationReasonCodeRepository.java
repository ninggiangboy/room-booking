package dev.ngb.backend.discovery.internal.repository.ranking;

import dev.ngb.backend.discovery.internal.model.ranking.RecommendationReasonCode;
import dev.ngb.backend.discovery.internal.model.ranking.ReasonCodeStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.ranking.ReasonCodeStatus;
import dev.ngb.backend.discovery.internal.model.ranking.RecommendationReasonCode;


/**
 * Reads the approved vocabulary an explanation may draw from.
 *
 * <p>Thresholds live on the row, so a caller that reads a code also reads the bar its claim has to
 * clear. The database refuses the claim if it does not.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code recommendation_reason_codes}.</p>
 */
public interface RecommendationReasonCodeRepository extends ListCrudRepository<RecommendationReasonCode, UUID> {

    /**
     * Lists every reason code currently approved for display.
     *
     * @param status normally {@code ACTIVE}
     * @return possibly empty list
     */
    List<RecommendationReasonCode> findByStatus(ReasonCodeStatus status);

    /**
     * Finds the active entry for one reason code.
     *
     * <pre>{@code
     * SELECT * FROM recommendation_reason_codes WHERE reason_code = :reasonCode AND status = 'ACTIVE'
     * }</pre>
     *
     * @param reasonCode the code
     * @return the active entry, when the code is live
     */
    @Query("SELECT * FROM recommendation_reason_codes WHERE reason_code = :reasonCode AND status = 'ACTIVE'")
    Optional<RecommendationReasonCode> findActive(@Param("reasonCode") String reasonCode);

    /**
     * Lists every version of one code, newest first, for the wording review.
     *
     * @param reasonCode the code
     * @return possibly empty list, newest vocabulary version first
     */
    List<RecommendationReasonCode> findByReasonCodeOrderByVocabularyVersionDesc(String reasonCode);
}
