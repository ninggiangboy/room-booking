package dev.ngb.backend.hostops.internal.repository.advice;

import dev.ngb.backend.hostops.internal.model.advice.HostAdviceDisclosure;
import dev.ngb.backend.hostops.internal.model.advice.HostAdviceKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a host was actually shown about one piece of advice.
 *
 * <p>Rows are append-only. Showing the same advice again writes a second disclosure rather than
 * editing the first, so the pairing of what was on the screen with what the host chose never has
 * to be guessed at from timestamps.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_advice_disclosures}.</p>
 */
public interface HostAdviceDisclosureRepository extends ListCrudRepository<HostAdviceDisclosure, UUID> {

    /**
     * Lists what one host was shown, newest first.
     *
     * @param recipientAccountHolderId the host
     * @return possibly empty list, most recently disclosed first
     */
    List<HostAdviceDisclosure> findByRecipientAccountHolderIdOrderByDisclosedAtDesc(
            UUID recipientAccountHolderId);

    /**
     * Lists the disclosures of one promotion suggestion, newest first.
     *
     * @param promotionSuggestionId the suggestion
     * @return possibly empty list, most recently disclosed first
     */
    List<HostAdviceDisclosure> findByPromotionSuggestionIdOrderByDisclosedAtDesc(
            UUID promotionSuggestionId);

    /**
     * Lists the disclosures of one price recommendation, newest first.
     *
     * @param priceRecommendationId the recommendation
     * @return possibly empty list, most recently disclosed first
     */
    List<HostAdviceDisclosure> findByPriceRecommendationIdOrderByDisclosedAtDesc(
            UUID priceRecommendationId);

    /**
     * Lists the advice a host was shown and has not answered, which is what an honest measure of
     * how often advice is simply ignored has to count.
     *
     * <pre>{@code
     * SELECT a.* FROM host_advice_disclosures a
     * LEFT JOIN host_advice_decisions x ON x.disclosure_id = a.id
     * WHERE a.recipient_account_holder_id = :recipientAccountHolderId AND x.id IS NULL
     * ORDER BY a.disclosed_at DESC
     * }</pre>
     *
     * @param recipientAccountHolderId the host
     * @return possibly empty list, most recently disclosed first
     */
    @Query("""
            SELECT a.* FROM host_advice_disclosures a
            LEFT JOIN host_advice_decisions x ON x.disclosure_id = a.id
            WHERE a.recipient_account_holder_id = :recipientAccountHolderId AND x.id IS NULL
            ORDER BY a.disclosed_at DESC
            """)
    List<HostAdviceDisclosure> findUnanswered(
            @Param("recipientAccountHolderId") UUID recipientAccountHolderId);

    /**
     * Counts what was shown of each kind of advice over a window, beside how much of it was
     * answered at all.
     *
     * <pre>{@code
     * SELECT a.advice_kind AS adviceKind,
     *        count(*) AS disclosedCount,
     *        count(x.id) AS answeredCount
     * FROM host_advice_disclosures a
     * LEFT JOIN host_advice_decisions x ON x.disclosure_id = a.id
     * WHERE a.disclosed_at >= :since
     * GROUP BY a.advice_kind
     * ORDER BY a.advice_kind
     * }</pre>
     *
     * <p>Advice nobody answers is not advice that was refused; it is advice nobody read. Counting
     * the two apart is what keeps a recommendation surface from being judged only by the people
     * who engaged with it.</p>
     *
     * @param since earliest disclosure instant to include
     * @return one row per kind of advice shown in the window
     */
    @Query("""
            SELECT a.advice_kind AS adviceKind,
                   count(*) AS disclosedCount,
                   count(x.id) AS answeredCount
            FROM host_advice_disclosures a
            LEFT JOIN host_advice_decisions x ON x.disclosure_id = a.id
            WHERE a.disclosed_at >= :since
            GROUP BY a.advice_kind
            ORDER BY a.advice_kind
            """)
    List<AdviceUptake> countUptake(@Param("since") Instant since);

    /**
     * How much of one kind of advice was shown, and how much of it the hosts answered.
     *
     * @param adviceKind the kind of advice
     * @param disclosedCount how many times it was shown
     * @param answeredCount how many of those the host answered
     */
    record AdviceUptake(HostAdviceKind adviceKind, long disclosedCount, long answeredCount) {}
}
