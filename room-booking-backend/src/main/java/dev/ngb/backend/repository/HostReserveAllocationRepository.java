package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostReserveAllocation;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads what went into and out of a reserve.
 *
 * <p>Append-only, so there is no amend path. A movement that should not have happened is answered by an
 * opposite movement.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_reserve_allocations}.</p>
 */
public interface HostReserveAllocationRepository extends ListCrudRepository<HostReserveAllocation, UUID> {

    /**
     * Returns a reserve's movements in order.
     *
     * <p>Spring derives {@code WHERE host_reserve_id = ? ORDER BY occurred_at}, matching
     * {@code idx_host_reserve_allocations_reserve}.</p>
     *
     * @param hostReserveId reserve whose movements are wanted
     * @return possibly empty list of movements, oldest first
     */
    List<HostReserveAllocation> findAllByHostReserveIdOrderByOccurredAt(UUID hostReserveId);

    /**
     * Returns the reserve movements funded by one allocation.
     *
     * <p>Spring derives {@code WHERE payable_allocation_id = ? ORDER BY occurred_at}, matching
     * {@code idx_host_reserve_allocations_allocation}. This is how a statement explains that a specific
     * night's earnings went into a reserve rather than to the host.</p>
     *
     * @param payableAllocationId allocation whose reserve movements are wanted
     * @return possibly empty list of movements, oldest first
     */
    List<HostReserveAllocation> findAllByPayableAllocationIdOrderByOccurredAt(
            UUID payableAllocationId);
}
