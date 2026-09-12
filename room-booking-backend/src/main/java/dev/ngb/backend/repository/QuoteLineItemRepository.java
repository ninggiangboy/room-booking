package dev.ngb.backend.repository;

import dev.ngb.backend.model.QuoteLineItem;
import dev.ngb.backend.model.QuoteLineType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the authoritative breakdown behind an offer.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code quote_line_items}.</p>
 *
 * <p>Loaded separately from {@code Quote} for the same reason as the nights: without lazy loading, an
 * owned collection would be fetched by every caller that only wanted a total.</p>
 */
public interface QuoteLineItemRepository extends ListCrudRepository<QuoteLineItem, UUID> {

    /**
     * Returns the lines of one offer in presentation order.
     *
     * <p>Spring derives {@code WHERE quote_id = ? ORDER BY line_number ASC}. The line number is
     * stable, so a receipt and a later reconciliation refer to the same line by the same position.</p>
     *
     * @param quoteId offer whose lines are wanted
     * @return possibly empty list, lowest line number first
     */
    List<QuoteLineItem> findAllByQuoteIdOrderByLineNumberAsc(UUID quoteId);

    /**
     * Returns the lines of one offer of a given kind.
     *
     * <p>Spring derives {@code WHERE quote_id = ? AND line_type = ?}. Used where settlement cares
     * about one category — commissionable accommodation, or the deposit that has to come back.</p>
     *
     * @param quoteId offer whose lines are wanted
     * @param lineType kind of line to include
     * @return possibly empty list of lines
     */
    List<QuoteLineItem> findAllByQuoteIdAndLineType(UUID quoteId, QuoteLineType lineType);
}
