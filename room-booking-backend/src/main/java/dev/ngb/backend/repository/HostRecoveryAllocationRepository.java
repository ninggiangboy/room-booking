package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostRecoveryAllocation;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the steps by which a host debt was collected.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_recovery_allocations}.</p>
 */
public interface HostRecoveryAllocationRepository extends ListCrudRepository<HostRecoveryAllocation, UUID> {

    /**
     * Returns a recovery's waterfall steps in order.
     *
     * <p>Spring derives {@code WHERE host_recovery_id = ? ORDER BY step_number}, matching
     * {@code uk_host_recovery_allocations_step}. The order is what shows a host that their reserve was
     * used before their future earnings were touched.</p>
     *
     * @param hostRecoveryId recovery whose steps are wanted
     * @return possibly empty list of steps, in waterfall order
     */
    List<HostRecoveryAllocation> findAllByHostRecoveryIdOrderByStepNumber(UUID hostRecoveryId);

    /**
     * Returns the recovery steps that consumed one allocation.
     *
     * <p>Spring derives {@code WHERE payable_allocation_id = ?}, matching
     * {@code idx_host_recovery_allocations_allocation}. This is how a statement explains that a specific
     * night's earnings went to a debt rather than to the host.</p>
     *
     * @param payableAllocationId allocation whose recovery steps are wanted
     * @return possibly empty list of steps
     */
    List<HostRecoveryAllocation> findAllByPayableAllocationId(UUID payableAllocationId);
}
