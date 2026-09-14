package dev.ngb.backend.ml.internal.repository.label;

import dev.ngb.backend.ml.internal.model.label.LabelDefinition;
import dev.ngb.backend.ml.internal.model.DefinitionStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the registry of prediction targets.
 *
 * <p>The horizon and the maturity delay live here rather than in a job, so that every observation
 * and every dataset build is measured against one definition of when an answer exists.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code label_definitions}.</p>
 */
public interface LabelDefinitionRepository extends ListCrudRepository<LabelDefinition, UUID> {

    /**
     * Finds one exact version of a target.
     *
     * @param labelKey the target
     * @param semanticVersion the version
     * @return the definition, when it is registered
     */
    Optional<LabelDefinition> findByLabelKeyAndSemanticVersion(String labelKey,
            short semanticVersion);

    /**
     * Lists every version of one target, newest registration first.
     *
     * @param labelKey the target
     * @return possibly empty list, most recently created first
     */
    List<LabelDefinition> findByLabelKeyOrderByCreatedAtDesc(String labelKey);

    /**
     * Lists the targets in one lifecycle state.
     *
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<LabelDefinition> findByStatus(DefinitionStatus status);

    /**
     * Lists the targets whose outcomes are only visible for cases an earlier system selected.
     * A dataset built from one of these has to carry that selection policy or it reproduces the
     * earlier system's blind spot.
     *
     * <pre>{@code
     * SELECT * FROM label_definitions
     * WHERE selection_bias_present AND status IN ('ACTIVE', 'DEPRECATED')
     * ORDER BY label_key
     * }</pre>
     *
     * @return possibly empty list, by target key
     */
    @Query("""
            SELECT * FROM label_definitions
            WHERE selection_bias_present AND status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY label_key
            """)
    List<LabelDefinition> findSelectionBiased();
}
