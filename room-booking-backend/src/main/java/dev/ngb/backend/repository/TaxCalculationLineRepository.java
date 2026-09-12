package dev.ngb.backend.repository;

import dev.ngb.backend.model.TaxCalculationLine;
import dev.ngb.backend.model.TaxRemittanceModel;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the per-jurisdiction detail of a tax determination.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code tax_calculation_lines}.</p>
 */
public interface TaxCalculationLineRepository extends ListCrudRepository<TaxCalculationLine, UUID> {

    /**
     * Returns the lines of one determination in order.
     *
     * <p>Spring derives {@code WHERE tax_calculation_id = ? ORDER BY line_number ASC}.</p>
     *
     * @param taxCalculationId determination whose lines are wanted
     * @return possibly empty list, lowest line number first
     */
    List<TaxCalculationLine> findAllByTaxCalculationIdOrderByLineNumberAsc(UUID taxCalculationId);

    /**
     * Returns the lines of one determination under a given remittance regime.
     *
     * <p>Spring derives {@code WHERE tax_calculation_id = ? AND remittance_model = ?}. Filing
     * separates the amounts the platform owes an authority from those the host owes, and the two go
     * on different returns.</p>
     *
     * @param taxCalculationId determination whose lines are wanted
     * @param remittanceModel regime to include
     * @return possibly empty list of lines
     */
    List<TaxCalculationLine> findAllByTaxCalculationIdAndRemittanceModel(
            UUID taxCalculationId, TaxRemittanceModel remittanceModel);
}
