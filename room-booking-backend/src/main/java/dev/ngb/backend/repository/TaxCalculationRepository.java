package dev.ngb.backend.repository;

import dev.ngb.backend.model.TaxCalculation;
import dev.ngb.backend.model.TaxCalculationStatus;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads tax determinations.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code tax_calculations}.</p>
 */
public interface TaxCalculationRepository extends ListCrudRepository<TaxCalculation, UUID> {

    /**
     * Returns the determination that currently stands for a quote.
     *
     * <p>Spring derives {@code WHERE quote_id = ? AND status = ?}. Callers pass
     * {@link TaxCalculationStatus#CALCULATED}; superseded and failed attempts stay in the table
     * because they explain what happened, but they must never be mistaken for the answer.</p>
     *
     * @param quoteId quote whose tax is wanted
     * @param status status to include
     * @return the determination, or empty when none stands
     */
    Optional<TaxCalculation> findByQuoteIdAndStatus(UUID quoteId, TaxCalculationStatus status);

    /**
     * Returns every determination made for a quote, including superseded and failed ones.
     *
     * <p>Spring derives {@code WHERE quote_id = ? ORDER BY calculated_at DESC}.</p>
     *
     * @param quoteId quote whose tax history is wanted
     * @return possibly empty list, most recent first
     */
    List<TaxCalculation> findAllByQuoteIdOrderByCalculatedAtDesc(UUID quoteId);
}
