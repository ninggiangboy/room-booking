package dev.ngb.backend.repository;

import dev.ngb.backend.model.MaintenanceRecord;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads defects in properties and what was asked about them.
 *
 * <p>The triage queue and the block-decision queue are the two operational reads; the block lookup
 * is what reconciles an inventory block back to the reason it exists.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code maintenance_records}.</p>
 */
public interface MaintenanceRecordRepository extends ListCrudRepository<MaintenanceRecord, UUID> {

    /**
     * Lists unresolved defects at a property, most serious first.
     *
     * <pre>{@code
     * SELECT *
     * FROM maintenance_records
     * WHERE property_id = :propertyId
     *   AND state IN ('REPORTED', 'TRIAGED', 'SCHEDULED', 'IN_PROGRESS')
     * ORDER BY severity, reported_at
     * }</pre>
     *
     * @param propertyId property
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM maintenance_records
            WHERE property_id = :propertyId
              AND state IN ('REPORTED', 'TRIAGED', 'SCHEDULED', 'IN_PROGRESS')
            ORDER BY severity, reported_at
            """)
    List<MaintenanceRecord> findOpenByProperty(@Param("propertyId") UUID propertyId);

    /**
     * Lists defects that make a property unfit and are waiting on inventory.
     *
     * <pre>{@code
     * SELECT *
     * FROM maintenance_records
     * WHERE guest_impact IN ('UNUSABLE', 'UNSAFE')
     *   AND block_request_state = 'REQUESTED'
     * ORDER BY reported_at
     * }</pre>
     *
     * <p>A row sitting here is a property still on sale that somebody has said is unsafe.</p>
     *
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM maintenance_records
            WHERE guest_impact IN ('UNUSABLE', 'UNSAFE')
              AND block_request_state = 'REQUESTED'
            ORDER BY reported_at
            """)
    List<MaintenanceRecord> findAwaitingBlock();

    /**
     * Finds the defects an inventory block was created for.
     *
     * <p>Spring derives {@code WHERE inventory_block_id = ?}, matching
     * {@code idx_maintenance_records_block}. Answers "why are these dates closed".</p>
     *
     * @param inventoryBlockId block inventory created
     * @return possibly empty list
     */
    List<MaintenanceRecord> findByInventoryBlockId(UUID inventoryBlockId);

    /**
     * Lists defects raised out of one incident.
     *
     * <p>Spring derives {@code WHERE source_incident_id = ?}.</p>
     *
     * @param sourceIncidentId incident
     * @return possibly empty list
     */
    List<MaintenanceRecord> findBySourceIncidentId(UUID sourceIncidentId);
}
