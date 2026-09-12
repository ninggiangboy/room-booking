package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExternalArtifactSource;
import dev.ngb.backend.model.ExternalFinancialArtifact;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the external evidence reconciliation runs against.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code external_financial_artifacts}.</p>
 */
public interface ExternalFinancialArtifactRepository extends ListCrudRepository<ExternalFinancialArtifact, UUID> {

    /**
     * Finds an artifact already ingested with identical content.
     *
     * <p>Spring derives {@code WHERE content_hash = ?}, matching
     * {@code uk_external_financial_artifacts_content}. A re-uploaded report resolves here rather than
     * being reconciled a second time and doubling every row it contains.</p>
     *
     * @param contentHash SHA-256 of the raw bytes, lowercase hex
     * @return the artifact, when those bytes have been seen
     */
    Optional<ExternalFinancialArtifact> findByContentHash(String contentHash);

    /**
     * Returns the artifacts covering a provider account, most recent coverage first.
     *
     * <p>Spring derives
     * {@code WHERE source_kind = ? AND provider_account_id = ? ORDER BY coverage_end DESC}, matching
     * {@code idx_external_financial_artifacts_coverage}.</p>
     *
     * @param sourceKind kind of evidence wanted
     * @param providerAccountId merchant account the evidence covers
     * @return possibly empty list of artifacts
     */
    List<ExternalFinancialArtifact> findAllBySourceKindAndProviderAccountIdOrderByCoverageEndDesc(
            ExternalArtifactSource sourceKind, UUID providerAccountId);

    /**
     * Returns artifacts that have arrived but have not been read.
     *
     * <pre>{@code
     * SELECT *
     * FROM external_financial_artifacts
     * WHERE parsed_at IS NULL
     * ORDER BY received_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_external_financial_artifacts_unparsed}. An artifact that is never parsed is a
     * control that silently stopped running, so this queue is monitored rather than merely drained.</p>
     *
     * @param batchSize most rows to return
     * @return possibly empty list of unparsed artifacts, oldest first
     */
    @Query("""
            SELECT *
            FROM external_financial_artifacts
            WHERE parsed_at IS NULL
            ORDER BY received_at
            LIMIT :batchSize
            """)
    List<ExternalFinancialArtifact> findUnparsed(@Param("batchSize") int batchSize);
}
