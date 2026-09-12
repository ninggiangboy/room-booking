package dev.ngb.backend.repository;

import dev.ngb.backend.model.BeneficialOwner;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads who ultimately owns or controls a business host.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code beneficial_owners}. Declarations are superseded rather than edited, so
 * the register as it stood when a past decision was made stays recoverable.</p>
 */
public interface BeneficialOwnerRepository extends ListCrudRepository<BeneficialOwner, UUID> {

    /**
     * Returns the current beneficial-owner register for a business host.
     *
     * <pre>{@code
     * SELECT *
     * FROM beneficial_owners
     * WHERE host_legal_profile_id = :profileId
     *   AND superseded_by IS NULL
     * }</pre>
     *
     * <p>Superseded rows are excluded because this is the register to screen against today; a past
     * register is read by following the supersession chain instead.</p>
     *
     * @param profileId business profile whose owners are wanted
     * @return possibly empty current register
     */
    @Query("""
            SELECT *
            FROM beneficial_owners
            WHERE host_legal_profile_id = :profileId
              AND superseded_by IS NULL
            """)
    List<BeneficialOwner> findCurrentRegister(@Param("profileId") UUID profileId);

    /**
     * Finds declared owners whose name hashes to a given digest.
     *
     * <p>Spring derives {@code WHERE full_name_digest = ?}. Matching on the digest keeps a screening
     * hit from requiring a plaintext scan of every declared owner. Several people can genuinely share
     * a name, so the result is a list and the date of birth distinguishes them.</p>
     *
     * @param fullNameDigest SHA-256 digest of the name being matched
     * @return possibly empty list of owners with that name
     */
    List<BeneficialOwner> findAllByFullNameDigest(String fullNameDigest);
}
