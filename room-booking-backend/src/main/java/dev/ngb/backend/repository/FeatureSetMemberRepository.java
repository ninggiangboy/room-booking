package dev.ngb.backend.repository;

import dev.ngb.backend.model.FeatureSetMember;
import dev.ngb.backend.model.FeatureDefinition;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads which feature versions belong to a feature set version.
 *
 * <p>The ordinal is part of the contract, so members are read in it: a vector whose column order
 * is ambiguous is one the serving path and the training path can disagree about silently.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code feature_set_members}.</p>
 */
public interface FeatureSetMemberRepository extends ListCrudRepository<FeatureSetMember, UUID> {

    /**
     * Lists one set version's membership in vector order.
     *
     * @param featureSetVersionId the set version
     * @return possibly empty list, by ordinal
     */
    List<FeatureSetMember> findByFeatureSetVersionIdOrderByOrdinal(UUID featureSetVersionId);

    /**
     * Lists the sets one feature version belongs to, for impact analysis before deprecation.
     *
     * @param featureDefinitionId the feature version
     * @return possibly empty list
     */
    List<FeatureSetMember> findByFeatureDefinitionId(UUID featureDefinitionId);

    /**
     * Lists the definitions behind one set version, in vector order, so the serving path can
     * resolve a whole set in one read.
     *
     * <pre>{@code
     * SELECT d.* FROM feature_definitions d
     * JOIN feature_set_members s ON s.feature_definition_id = d.id
     * WHERE s.feature_set_version_id = :featureSetVersionId
     * ORDER BY s.ordinal
     * }</pre>
     *
     * @param featureSetVersionId the set version
     * @return possibly empty list, by ordinal
     */
    @Query("""
            SELECT d.* FROM feature_definitions d
            JOIN feature_set_members s ON s.feature_definition_id = d.id
            WHERE s.feature_set_version_id = :featureSetVersionId
            ORDER BY s.ordinal
            """)
    List<FeatureDefinition> findDefinitionsInOrder(
            @Param("featureSetVersionId") UUID featureSetVersionId);
}
