package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingModificationDelta;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads what a modification proposal would change, dimension by dimension.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code booking_modification_deltas}.</p>
 */
public interface BookingModificationDeltaRepository extends ListCrudRepository<BookingModificationDelta, UUID> {

    /**
     * Returns one proposal's deltas in order.
     *
     * <p>Spring derives {@code WHERE proposal_id = ? ORDER BY dimension, sequence_number}, matching
     * {@code idx_booking_modification_deltas_proposal}.</p>
     *
     * @param proposalId proposal whose deltas are wanted
     * @return possibly empty list, grouped by dimension
     */
    List<BookingModificationDelta> findAllByProposalIdOrderByDimensionAscSequenceNumberAsc(
            UUID proposalId);
}
