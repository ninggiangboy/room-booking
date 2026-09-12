package dev.ngb.backend.repository;

import dev.ngb.backend.model.CommunicationConsent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads consent evidence.
 *
 * <p>Withdrawal is immediate for everything sent afterwards, so the live read is the one the dispatch
 * path rechecks before claiming a suppressible intent.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code communication_consents}.</p>
 */
public interface CommunicationConsentRepository extends ListCrudRepository<CommunicationConsent, UUID> {

    /**
     * Finds the live consent for a category and channel.
     *
     * <pre>{@code
     * SELECT *
     * FROM communication_consents
     * WHERE account_holder_id = :accountHolderId
     *   AND category_code = :categoryCode
     *   AND channel = :channel
     *   AND (market_code = :marketCode OR market_code IS NULL)
     *   AND withdrawn_at IS NULL
     * ORDER BY market_code NULLS LAST
     * LIMIT 1
     * }</pre>
     *
     * @param accountHolderId recipient
     * @param categoryCode category being evaluated
     * @param channel channel being considered
     * @param marketCode market whose rules apply
     * @return the live consent, when one exists
     */
    @Query("""
            SELECT *
            FROM communication_consents
            WHERE account_holder_id = :accountHolderId
              AND category_code = :categoryCode
              AND channel = :channel
              AND (market_code = :marketCode OR market_code IS NULL)
              AND withdrawn_at IS NULL
            ORDER BY market_code NULLS LAST
            LIMIT 1
            """)
    Optional<CommunicationConsent> findLive(@Param("accountHolderId") UUID accountHolderId,
                                            @Param("categoryCode") String categoryCode,
                                            @Param("channel") String channel,
                                            @Param("marketCode") String marketCode);

    /**
     * Returns a recipient's whole consent history.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ? ORDER BY granted_at DESC}. Withdrawn rows stay,
     * because they are the evidence that permission existed when something was sent.</p>
     *
     * @param accountHolderId recipient whose history is wanted
     * @return possibly empty list, newest grant first
     */
    List<CommunicationConsent> findAllByAccountHolderIdOrderByGrantedAtDesc(UUID accountHolderId);
}
