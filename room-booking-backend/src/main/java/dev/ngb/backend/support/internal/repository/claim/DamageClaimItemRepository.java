package dev.ngb.backend.support.internal.repository.claim;

import dev.ngb.backend.support.internal.model.claim.DamageClaimItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads the itemised losses of a damage claim.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code damage_claim_items}.</p>
 */
public interface DamageClaimItemRepository extends ListCrudRepository<DamageClaimItem, UUID> {

    /**
     * Lists the items of a claim in line order.
     *
     * @param damageClaimId claim
     * @return possibly empty list, in line order
     */
    List<DamageClaimItem> findByDamageClaimIdOrderByLineNumber(UUID damageClaimId);

    /**
     * Sums what has been accepted across a claim’s items.
     *
     * <pre>{@code
     * SELECT coalesce(sum(accepted_amount_minor), 0)
     * FROM damage_claim_items
     * WHERE damage_claim_id = :damageClaimId
     * }</pre>
     *
     * <p>The same figure the deferred balance trigger computes at commit; reading it beforehand lets a
     * caller show the number rather than discover it in an exception.</p>
     *
     * @param damageClaimId claim
     * @return total accepted in minor units, zero when nothing has been priced
     */
    @Query("""
            SELECT coalesce(sum(accepted_amount_minor), 0)
            FROM damage_claim_items
            WHERE damage_claim_id = :damageClaimId
            """)
    long sumAccepted(@Param("damageClaimId") UUID damageClaimId);
}
