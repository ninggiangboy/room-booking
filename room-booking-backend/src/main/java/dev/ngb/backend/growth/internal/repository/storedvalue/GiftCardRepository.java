package dev.ngb.backend.growth.internal.repository.storedvalue;

import dev.ngb.backend.growth.internal.model.storedvalue.GiftCard;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the gift cards issued, and what became of the value on each one.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code gift_cards}.</p>
 */
public interface GiftCardRepository extends ListCrudRepository<GiftCard, UUID> {

    /**
     * Finds a card by the serial it is known by.
     *
     * @param serialReference the serial
     * @return the card, when it exists
     */
    Optional<GiftCard> findBySerialReference(String serialReference);

    /**
     * Finds the card a redemption code belongs to.
     *
     * @param codeDigest digest of the code somebody typed in
     * @return the card, when the code matches one
     */
    Optional<GiftCard> findByCodeDigest(String codeDigest);

    /**
     * Lists the cards one person bought.
     *
     * @param purchaserAccountHolderId the purchaser
     * @return possibly empty list
     */
    List<GiftCard> findByPurchaserAccountHolderId(UUID purchaserAccountHolderId);

    /**
     * Lists the cards that have expired with value still on them, which is the set breakage is
     * recognised from and nothing else may be.
     *
     * <pre>{@code
     * SELECT * FROM gift_cards
     * WHERE state = 'ACTIVE'
     *   AND breakage_policy = 'EXPIRES_TO_BREAKAGE'
     *   AND expires_at IS NOT NULL
     *   AND expires_at <= :at
     *   AND breakage_recognized_at IS NULL
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the recognition run is for
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM gift_cards
            WHERE state = 'ACTIVE'
              AND breakage_policy = 'EXPIRES_TO_BREAKAGE'
              AND expires_at IS NOT NULL
              AND expires_at <= :at
              AND breakage_recognized_at IS NULL
            ORDER BY expires_at
            """)
    List<GiftCard> findBreakageCandidates(@Param("at") Instant at);

    /**
     * Sums the face value issued by one legal entity and not yet redeemed, which is the liability
     * that entity actually carries.
     *
     * <pre>{@code
     * SELECT coalesce(sum(face_value_minor), 0) FROM gift_cards
     * WHERE issuing_legal_entity_id = :issuingLegalEntityId
     *   AND state IN ('ISSUED', 'ACTIVE')
     * }</pre>
     *
     * @param issuingLegalEntityId the issuing entity
     * @return outstanding face value, in integer minor units
     */
    @Query("""
            SELECT coalesce(sum(face_value_minor), 0) FROM gift_cards
            WHERE issuing_legal_entity_id = :issuingLegalEntityId
              AND state IN ('ISSUED', 'ACTIVE')
            """)
    long sumOutstanding(@Param("issuingLegalEntityId") UUID issuingLegalEntityId);
}
