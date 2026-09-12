package dev.ngb.backend.repository;

import dev.ngb.backend.model.RelocationOffer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads replacement stays offered to a guest.
 *
 * <p>Declined offers are read as well as accepted ones: three declines is evidence about the offers.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code relocation_offers}.</p>
 */
public interface RelocationOfferRepository extends ListCrudRepository<RelocationOffer, UUID> {

    /**
     * Returns one case's offers in the order they were made.
     *
     * <p>Spring derives {@code WHERE relocation_case_id = ? ORDER BY sequence_number}.</p>
     *
     * @param relocationCaseId case whose offers are wanted
     * @return possibly empty list, first offer first
     */
    List<RelocationOffer> findAllByRelocationCaseIdOrderBySequenceNumber(UUID relocationCaseId);

    /**
     * Returns the offer a case settled on, if it has.
     *
     * <pre>{@code
     * SELECT *
     * FROM relocation_offers
     * WHERE relocation_case_id = :relocationCaseId AND state = 'ACCEPTED'
     * }</pre>
     *
     * <p>At most one row can match: {@code uk_relocation_offers_accepted} covers exactly this predicate,
     * because a guest cannot be relocated into two stays.</p>
     *
     * @param relocationCaseId case to check
     * @return the accepted offer, when one exists
     */
    @Query("""
            SELECT *
            FROM relocation_offers
            WHERE relocation_case_id = :relocationCaseId AND state = 'ACCEPTED'
            """)
    Optional<RelocationOffer> findAccepted(@Param("relocationCaseId") UUID relocationCaseId);
}
