package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostTaxIdentifier;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the tax identifiers a host has declared.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code host_tax_identifiers}. No method returns a tax code: rows carry a digest
 * and the last few characters, with the value itself behind the secret boundary.</p>
 */
public interface HostTaxIdentifierRepository extends ListCrudRepository<HostTaxIdentifier, UUID> {

    /**
     * Finds the live identifier of one type for a profile and market.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_tax_identifiers
     * WHERE host_legal_profile_id = :profileId
     *   AND market_code = :marketCode
     *   AND identifier_type = :identifierType
     *   AND effective_until IS NULL
     * }</pre>
     *
     * <p>{@code uk_host_tax_identifiers_live} guarantees at most one row matches. The caller must
     * still check {@code registrationStatus}: a declared identifier is one the host typed, and
     * treating it as validated is how withholding gets applied at the wrong rate.</p>
     *
     * @param profileId legal profile in question
     * @param marketCode market the identifier applies in
     * @param identifierType kind of identifier required
     * @return the live identifier when one is declared
     */
    @Query("""
            SELECT *
            FROM host_tax_identifiers
            WHERE host_legal_profile_id = :profileId
              AND market_code = :marketCode
              AND identifier_type = :identifierType
              AND effective_until IS NULL
            """)
    Optional<HostTaxIdentifier> findLive(
            @Param("profileId") UUID profileId,
            @Param("marketCode") String marketCode,
            @Param("identifierType") String identifierType);

    /**
     * Returns every identifier a profile has declared, newest first.
     *
     * <p>Spring derives {@code WHERE host_legal_profile_id = ? ORDER BY effective_from DESC},
     * including superseded rows, which reconstructing a historical invoice needs.</p>
     *
     * @param hostLegalProfileId legal profile whose identifiers are listed
     * @return possibly empty list of identifiers, newest first
     */
    List<HostTaxIdentifier> findAllByHostLegalProfileIdOrderByEffectiveFromDesc(
            UUID hostLegalProfileId);
}
