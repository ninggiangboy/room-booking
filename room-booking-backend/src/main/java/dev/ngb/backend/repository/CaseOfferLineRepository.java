package dev.ngb.backend.repository;

import dev.ngb.backend.model.CaseOfferLine;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the terms of an offer.
 *
 * <p>Writable only while the offer is a draft: once it has been sent, its lines are what the other
 * party saw.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_offer_lines}.</p>
 */
public interface CaseOfferLineRepository extends ListCrudRepository<CaseOfferLine, UUID> {

    /**
     * Lists the lines of an offer in line order.
     *
     * @param caseOfferId offer
     * @return possibly empty list, in line order
     */
    List<CaseOfferLine> findByCaseOfferIdOrderByLineNumber(UUID caseOfferId);
}
