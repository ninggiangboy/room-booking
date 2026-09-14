package dev.ngb.backend.ml.internal.repository.feature;

import dev.ngb.backend.ml.internal.model.feature.FeatureSetVersion;
import dev.ngb.backend.ml.internal.model.feature.FeatureSetStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the frozen input contracts models are registered against.
 *
 * <p>A model version names one of these, and that name is what makes "this model may not read
 * features it did not declare" checkable rather than aspirational.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code feature_set_versions}.</p>
 */
public interface FeatureSetVersionRepository extends ListCrudRepository<FeatureSetVersion, UUID> {

    /**
     * Finds one exact version of a feature set.
     *
     * @param setKey the set
     * @param semanticVersion the version
     * @return the set version, when it is registered
     */
    Optional<FeatureSetVersion> findBySetKeyAndSemanticVersion(String setKey,
            short semanticVersion);

    /**
     * Lists every version of one set, newest registration first.
     *
     * @param setKey the set
     * @return possibly empty list, most recently created first
     */
    List<FeatureSetVersion> findBySetKeyOrderByCreatedAtDesc(String setKey);

    /**
     * Lists the set versions in one lifecycle state.
     *
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<FeatureSetVersion> findByStatus(FeatureSetStatus status);

    /**
     * Finds the set version with a given membership, so that rebuilding an identical set
     * returns the existing one rather than registering a second.
     *
     * <pre>{@code
     * SELECT * FROM feature_set_versions
     * WHERE set_key = :setKey AND member_digest = :memberDigest
     * }</pre>
     *
     * @param setKey the set
     * @param memberDigest digest over the member definition versions
     * @return the existing set version, when one has the same membership
     */
    @Query("""
            SELECT * FROM feature_set_versions
            WHERE set_key = :setKey AND member_digest = :memberDigest
            """)
    Optional<FeatureSetVersion> findByMembership(@Param("setKey") String setKey,
            @Param("memberDigest") String memberDigest);
}
