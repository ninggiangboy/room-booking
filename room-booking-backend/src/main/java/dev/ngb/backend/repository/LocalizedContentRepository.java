package dev.ngb.backend.repository;

import dev.ngb.backend.model.LocalizedContent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the exact approved wording shown to a reader, including for historical replay.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code localized_contents}. Content versions are immutable, so nothing here updates one.</p>
 */
public interface LocalizedContentRepository extends ListCrudRepository<LocalizedContent, UUID> {

    /**
     * Resolves the approved rendering of a message for a locale at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM localized_contents
     * WHERE content_key = :contentKey
     *   AND locale = :locale
     *   AND lifecycle_state = 'APPROVED'
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     * ORDER BY content_version DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>Only approved content is selectable, and the caller binds its own instant so that
     * re-rendering what a guest accepted returns the wording they actually saw rather than the
     * current one. An empty result is a missing translation, which callers surface explicitly rather
     * than papering over with a key name.</p>
     *
     * @param contentKey stable semantic key
     * @param locale BCP 47 locale requested
     * @param decisionInstant instant the wording should be resolved as of
     * @return the approved rendering, when one exists for that locale
     */
    @Query("""
            SELECT *
            FROM localized_contents
            WHERE content_key = :contentKey
              AND locale = :locale
              AND lifecycle_state = 'APPROVED'
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
            ORDER BY content_version DESC
            LIMIT 1
            """)
    Optional<LocalizedContent> findApproved(
            @Param("contentKey") String contentKey,
            @Param("locale") String locale,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Finds one exact content version.
     *
     * <p>Spring derives {@code WHERE content_key = ? AND locale = ? AND content_version = ?},
     * matching {@code uk_localized_contents_version}. This is the lookup a recorded acceptance uses:
     * it cites a version, and that version must resolve verbatim regardless of approval state
     * today.</p>
     *
     * @param contentKey stable semantic key
     * @param locale BCP 47 locale
     * @param contentVersion exact version cited by the acceptance
     * @return the cited version when it exists
     */
    Optional<LocalizedContent> findByContentKeyAndLocaleAndContentVersion(
            String contentKey,
            String locale,
            int contentVersion);
}
