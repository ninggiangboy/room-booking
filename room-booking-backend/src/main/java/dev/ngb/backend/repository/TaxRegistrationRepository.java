package dev.ngb.backend.repository;

import dev.ngb.backend.model.TaxRegistration;
import dev.ngb.backend.model.TaxType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads parties' registrations with tax authorities.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code tax_registrations}.</p>
 *
 * <p>No method returns or accepts a registration number. Lookups go through the digest, which
 * recognises a resubmission without the plaintext ever reaching this layer.</p>
 */
public interface TaxRegistrationRepository extends ListCrudRepository<TaxRegistration, UUID> {

    /**
     * Returns the registration in force for a party, jurisdiction, and tax at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM tax_registrations
     * WHERE party_tax_profile_id = :partyTaxProfileId
     *   AND jurisdiction_code = :jurisdictionCode
     *   AND tax_type = :taxType
     *   AND effective_from <= :instant
     *   AND (effective_until IS NULL OR effective_until > :instant)
     * }</pre>
     *
     * <p>At most one row can match; the exclusion constraint forbids overlapping registrations for
     * one party and tax.</p>
     *
     * @param partyTaxProfileId profile whose registration is wanted
     * @param jurisdictionCode jurisdiction being filed in
     * @param taxType tax being filed
     * @param instant instant the tax decision applies to
     * @return the registration in force, or empty when there is none
     */
    @Query("""
            SELECT *
            FROM tax_registrations
            WHERE party_tax_profile_id = :partyTaxProfileId
              AND jurisdiction_code = :jurisdictionCode
              AND tax_type = :taxType
              AND effective_from <= :instant
              AND (effective_until IS NULL OR effective_until > :instant)
            """)
    Optional<TaxRegistration> findInForce(
            @Param("partyTaxProfileId") UUID partyTaxProfileId,
            @Param("jurisdictionCode") String jurisdictionCode,
            @Param("taxType") TaxType taxType,
            @Param("instant") Instant instant);

    /**
     * Finds a party's registration by the digest of the number supplied.
     *
     * <p>Spring derives {@code WHERE party_tax_profile_id = ? AND registration_digest = ?}. Lets a
     * resubmitted number be recognised as one already on file without the plaintext being stored,
     * compared, or logged anywhere in this layer.</p>
     *
     * @param partyTaxProfileId profile the number was supplied for
     * @param registrationDigest lowercase hex SHA-256 of the number
     * @return the existing registration, or empty when it is new
     */
    Optional<TaxRegistration> findByPartyTaxProfileIdAndRegistrationDigest(
            UUID partyTaxProfileId, String registrationDigest);

    /**
     * Returns every registration held under one profile.
     *
     * <p>Spring derives {@code WHERE party_tax_profile_id = ?}.</p>
     *
     * @param partyTaxProfileId profile whose registrations are wanted
     * @return possibly empty list of registrations
     */
    List<TaxRegistration> findAllByPartyTaxProfileId(UUID partyTaxProfileId);
}
