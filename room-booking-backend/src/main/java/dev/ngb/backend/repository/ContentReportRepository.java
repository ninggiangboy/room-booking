package dev.ngb.backend.repository;

import dev.ngb.backend.model.ContentReport;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads allegations about content.
 *
 * <p>Reporter identity is confidential by default, so anything reading these rows for a reported party
 * must project rather than return them whole.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code content_reports}.</p>
 */
public interface ContentReportRepository extends ListCrudRepository<ContentReport, UUID> {

    /**
     * Reads the reports about one revision.
     *
     * <pre>{@code
     * SELECT * FROM content_reports WHERE content_revision_id = :contentRevisionId
     * ORDER BY reported_at
     * }</pre>
     *
     * @param contentRevisionId the revision
     * @return possibly empty list, oldest first
     */
    List<ContentReport> findByContentRevisionIdOrderByReportedAt(UUID contentRevisionId);

    /**
     * Lists unanswered reports in one severity band.
     *
     * <pre>{@code
     * SELECT * FROM content_reports
     * WHERE severity_band = :severityBand AND adjudicated_at IS NULL
     * ORDER BY reported_at
     * }</pre>
     *
     * @param severityBand how urgent
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM content_reports
            WHERE severity_band = :severityBand AND adjudicated_at IS NULL
            ORDER BY reported_at
            """)
    List<ContentReport> findOpenBySeverity(@Param("severityBand") String severityBand);

    /**
     * Reads the reports joined into one investigation.
     *
     * <pre>{@code
     * SELECT * FROM content_reports WHERE dedupe_group_id = :dedupeGroupId ORDER BY reported_at
     * }</pre>
     *
     * <p>Duplicate reports may share an investigation while keeping separate evidence and separate
     * acknowledgement to each reporter.</p>
     *
     * @param dedupeGroupId the investigation group
     * @return possibly empty list, oldest first
     */
    List<ContentReport> findByDedupeGroupIdOrderByReportedAt(UUID dedupeGroupId);
}
