package dev.ngb.backend.repository;

import dev.ngb.backend.model.MessageTranslation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads stored translations.
 *
 * <p>Append-only: a better engine adds a row rather than changing what a reader was shown.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code message_translations}.</p>
 */
public interface MessageTranslationRepository extends ListCrudRepository<MessageTranslation, UUID> {

    /**
     * Finds the translation of a message into a locale.
     *
     * <pre>{@code
     * SELECT *
     * FROM message_translations
     * WHERE message_id = :messageId
     *   AND source_revision_number = :sourceRevisionNumber
     *   AND target_locale = :targetLocale
     * ORDER BY created_at DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>Ordered rather than keyed, because the same text may have been translated by several engine
     * versions and the newest is the one to show.</p>
     *
     * @param messageId message being read
     * @param sourceRevisionNumber which text was translated; zero for the original
     * @param targetLocale locale the reader wants
     * @return the newest translation, when one exists
     */
    @Query("""
            SELECT *
            FROM message_translations
            WHERE message_id = :messageId
              AND source_revision_number = :sourceRevisionNumber
              AND target_locale = :targetLocale
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<MessageTranslation> findNewest(@Param("messageId") UUID messageId,
                                            @Param("sourceRevisionNumber") short sourceRevisionNumber,
                                            @Param("targetLocale") String targetLocale);

    /**
     * Returns every translation held for a message.
     *
     * <p>Spring derives {@code WHERE message_id = ?}.</p>
     *
     * @param messageId message being read
     * @return possibly empty list
     */
    List<MessageTranslation> findAllByMessageId(UUID messageId);
}
