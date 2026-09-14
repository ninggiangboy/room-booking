package dev.ngb.backend.admin.internal.repository.role;

import dev.ngb.backend.admin.internal.model.role.BreakGlassActivity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads what was actually done under emergency access.
 *
 * <p>Append-only. The review of a grant is a review of these rows, so the questions they answer --
 * what was reached, how much of it, and how sensitive it was -- are the ones asked here.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code break_glass_activities}.</p>
 */
public interface BreakGlassActivityRepository extends ListCrudRepository<BreakGlassActivity, UUID> {

    /**
     * Lists what one grant touched, in order.
     *
     * @param breakGlassGrantId the grant
     * @return possibly empty list, in sequence
     */
    List<BreakGlassActivity> findByBreakGlassGrantIdOrderBySequenceNumber(
            UUID breakGlassGrantId);

    /**
     * Lists the emergency access taken against one target, which is how the question "did anybody
     * reach this outside the ordinary path" is answered.
     *
     * <pre>{@code
     * SELECT * FROM break_glass_activities
     * WHERE target_type = :targetType AND target_id = :targetId
     * ORDER BY occurred_at DESC
     * }</pre>
     *
     * @param targetType kind of thing
     * @param targetId the row
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM break_glass_activities
            WHERE target_type = :targetType AND target_id = :targetId
            ORDER BY occurred_at DESC
            """)
    List<BreakGlassActivity> findForTarget(@Param("targetType") String targetType,
            @Param("targetId") UUID targetId);

    /**
     * Lists the emergency access that reached personal or restricted data in a window, which is
     * what a privacy report reads.
     *
     * <pre>{@code
     * SELECT * FROM break_glass_activities
     * WHERE data_sensitivity IN ('PERSONAL', 'RESTRICTED')
     *   AND occurred_at >= :from AND occurred_at < :to
     * ORDER BY occurred_at
     * }</pre>
     *
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM break_glass_activities
            WHERE data_sensitivity IN ('PERSONAL', 'RESTRICTED')
              AND occurred_at >= :from AND occurred_at < :to
            ORDER BY occurred_at
            """)
    List<BreakGlassActivity> findSensitiveBetween(@Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Sums how many rows one grant reached, so a bulk read taken under emergency access is visible
     * as one rather than as a long list of ordinary-looking entries.
     *
     * <pre>{@code
     * SELECT coalesce(sum(row_count), 0) FROM break_glass_activities
     * WHERE break_glass_grant_id = :breakGlassGrantId
     * }</pre>
     *
     * @param breakGlassGrantId the grant
     * @return total rows reached, zero when nothing was
     */
    @Query("""
            SELECT coalesce(sum(row_count), 0) FROM break_glass_activities
            WHERE break_glass_grant_id = :breakGlassGrantId
            """)
    long sumRowsReached(@Param("breakGlassGrantId") UUID breakGlassGrantId);
}
