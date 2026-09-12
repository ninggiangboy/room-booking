package dev.ngb.backend.repository;

import dev.ngb.backend.model.InstructionSet;
import dev.ngb.backend.model.InstructionSetStatus;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads versioned arrival instructions.
 *
 * <p>Retrieval evaluates release conditions against authoritative facts every time; this repository
 * only finds the version those conditions should be evaluated against.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code instruction_sets}.</p>
 */
public interface InstructionSetRepository extends ListCrudRepository<InstructionSet, UUID> {

    /**
     * Finds the version currently in force for a stay.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? AND status = ?}, matching
     * {@code uk_instruction_sets_current} when the status is {@code RELEASED}. At most one released
     * version can exist per stay.</p>
     *
     * @param operationalStayId stay
     * @param status status to match, normally {@code RELEASED}
     * @return the current version, when one is released
     */
    Optional<InstructionSet> findByOperationalStayIdAndStatus(UUID operationalStayId,
            InstructionSetStatus status);

    /**
     * Lists every version written for a stay, newest first.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? ORDER BY version_number DESC}. Superseded
     * versions are kept deliberately: an access audit row points at the version it served.</p>
     *
     * @param operationalStayId stay
     * @return possibly empty list, newest version first
     */
    List<InstructionSet> findByOperationalStayIdOrderByVersionNumberDesc(UUID operationalStayId);

    /**
     * Finds one numbered version of a stay's instructions.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? AND version_number = ?}, matching
     * {@code uk_instruction_sets_version}.</p>
     *
     * @param operationalStayId stay
     * @param versionNumber version within that stay
     * @return the version, when it exists
     */
    Optional<InstructionSet> findByOperationalStayIdAndVersionNumber(UUID operationalStayId,
            int versionNumber);
}
