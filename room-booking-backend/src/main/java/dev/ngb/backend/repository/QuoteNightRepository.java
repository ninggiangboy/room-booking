package dev.ngb.backend.repository;

import dev.ngb.backend.model.QuoteNight;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the per-night detail of an offer.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code quote_nights}.</p>
 *
 * <p>Loaded through this repository rather than as part of {@code Quote}: Spring Data JDBC has no
 * lazy loading, so an aggregate owning its nights would fetch them on every read, including the many
 * that only need the summary.</p>
 */
public interface QuoteNightRepository extends ListCrudRepository<QuoteNight, UUID> {

    /**
     * Returns the nights of one offer in stay order.
     *
     * <p>Spring derives {@code WHERE quote_id = ? ORDER BY stay_date ASC}. Stay order is what a guest
     * sees and what a partial refund works through.</p>
     *
     * @param quoteId offer whose nights are wanted
     * @return possibly empty list, earliest night first
     */
    List<QuoteNight> findAllByQuoteIdOrderByStayDateAsc(UUID quoteId);
}
