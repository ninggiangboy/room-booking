package dev.ngb.backend.repository;

import dev.ngb.backend.model.ModelApproval;
import dev.ngb.backend.model.ModelApprovalRole;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads which functions have signed off on a model version.
 *
 * <p>A promotion is checked against these rows, not against a field somebody set. The scope of
 * each approval is recorded so a later request for more traffic or a new market can be compared
 * with what was actually granted.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code model_approvals}.</p>
 */
public interface ModelApprovalRepository extends ListCrudRepository<ModelApproval, UUID> {

    /**
     * Lists the decisions recorded for one version.
     *
     * @param modelVersionId the version
     * @return possibly empty list
     */
    List<ModelApproval> findByModelVersionId(UUID modelVersionId);

    /**
     * Finds one function's decision on a version.
     *
     * @param modelVersionId the version
     * @param approvalRole the reviewing function
     * @return the decision, when that function has recorded one
     */
    Optional<ModelApproval> findByModelVersionIdAndApprovalRole(UUID modelVersionId,
            ModelApprovalRole approvalRole);

    /**
     * Lists the roles that have approved a version, which is what a promotion gate compares
     * against the roles the impact class requires.
     *
     * <pre>{@code
     * SELECT * FROM model_approvals
     * WHERE model_version_id = :modelVersionId AND decision = 'APPROVED'
     * ORDER BY approval_role
     * }</pre>
     *
     * @param modelVersionId the version
     * @return possibly empty list, by role
     */
    @Query("""
            SELECT * FROM model_approvals
            WHERE model_version_id = :modelVersionId AND decision = 'APPROVED'
            ORDER BY approval_role
            """)
    List<ModelApproval> findGranted(@Param("modelVersionId") UUID modelVersionId);
}
