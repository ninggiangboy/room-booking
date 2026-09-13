package dev.ngb.backend.repository;

import dev.ngb.backend.model.RiskSignal;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads observations.
 *
 * <p>Append-only, so there is no update path here. The dedupe lookup is what an ingest worker uses
 * before writing, and the replay window is bounded by both the event time and the arrival time so a
 * point-in-time reconstruction can exclude what had not arrived yet.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_signals}.</p>
 */
public interface RiskSignalRepository extends ListCrudRepository<RiskSignal, UUID> {

    /**
     * Finds an observation by the source that produced it.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_signals
     * WHERE source_domain = :sourceDomain
     *   AND provider_account_id IS NOT DISTINCT FROM :providerAccountId
     *   AND source_record_id = :sourceRecordId
     * }</pre>
     *
     * <p>Matches {@code uk_risk_signals_source}, which treats nulls as equal, so a redelivery resolves
     * to the row already written rather than to nothing.</p>
     *
     * @param sourceDomain producing domain
     * @param providerAccountId provider account, or null for a platform observation
     * @param sourceRecordId source system's identifier
     * @return the observation, when it has already arrived
     */
    @Query("""
            SELECT *
            FROM risk_signals
            WHERE source_domain = :sourceDomain
              AND provider_account_id IS NOT DISTINCT FROM :providerAccountId
              AND source_record_id = :sourceRecordId
            """)
    Optional<RiskSignal> findBySource(@Param("sourceDomain") String sourceDomain,
                                      @Param("providerAccountId") UUID providerAccountId,
                                      @Param("sourceRecordId") String sourceRecordId);

    /**
     * Reads observations of one type that had arrived by a given instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_signals
     * WHERE signal_type = :signalType
     *   AND event_time >= :from
     *   AND event_time < :to
     *   AND received_at <= :knownBy
     * ORDER BY event_time
     * }</pre>
     *
     * <p>The arrival bound is what makes replay honest: evidence that reached the platform after the
     * original decision must not appear in a reconstruction of it.</p>
     *
     * @param signalType kind of observation
     * @param from inclusive start of the event-time window
     * @param to exclusive end of it
     * @param knownBy latest arrival instant to include
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM risk_signals
            WHERE signal_type = :signalType
              AND event_time >= :from
              AND event_time < :to
              AND received_at <= :knownBy
            ORDER BY event_time
            """)
    List<RiskSignal> findForReplay(@Param("signalType") String signalType,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   @Param("knownBy") Instant knownBy);

    /**
     * Finds observations that correct a given one.
     *
     * <pre>{@code
     * SELECT * FROM risk_signals WHERE corrects_signal_id = :correctsSignalId
     * }</pre>
     *
     * @param correctsSignalId the observation that was corrected
     * @return possibly empty list
     */
    List<RiskSignal> findByCorrectsSignalId(UUID correctsSignalId);
}
