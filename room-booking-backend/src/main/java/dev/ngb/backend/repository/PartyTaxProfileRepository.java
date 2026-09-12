package dev.ngb.backend.repository;

import dev.ngb.backend.model.PartyTaxProfile;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads where parties stand for tax.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code party_tax_profiles}.</p>
 */
public interface PartyTaxProfileRepository extends ListCrudRepository<PartyTaxProfile, UUID> {

    /**
     * Returns the profile in force for an account holder at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM party_tax_profiles
     * WHERE account_holder_id = :accountHolderId
     *   AND effective_from <= :instant
     *   AND (effective_until IS NULL OR effective_until > :instant)
     * }</pre>
     *
     * <p>At most one row can match, because the exclusion constraint forbids overlapping profiles for
     * one party. Taxing a past stay passes that stay's instant, so a host who has since moved country
     * is still taxed where they were when the stay happened.</p>
     *
     * @param accountHolderId party being taxed
     * @param instant instant the tax decision applies to
     * @return the profile in force, or empty when the party has none
     */
    @Query("""
            SELECT *
            FROM party_tax_profiles
            WHERE account_holder_id = :accountHolderId
              AND effective_from <= :instant
              AND (effective_until IS NULL OR effective_until > :instant)
            """)
    Optional<PartyTaxProfile> findInForceForAccountHolder(
            @Param("accountHolderId") UUID accountHolderId,
            @Param("instant") Instant instant);

    /**
     * Returns every profile an account holder has had, newest first.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ? ORDER BY effective_from DESC}.</p>
     *
     * @param accountHolderId party whose history is wanted
     * @return possibly empty list, most recent period first
     */
    List<PartyTaxProfile> findAllByAccountHolderIdOrderByEffectiveFromDesc(UUID accountHolderId);
}
