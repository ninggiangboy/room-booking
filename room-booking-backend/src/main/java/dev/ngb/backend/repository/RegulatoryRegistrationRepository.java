package dev.ngb.backend.repository;

import dev.ngb.backend.model.RegulatoryRegistration;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads local authorisations to let accommodation, and the limits attached to them.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code regulatory_registrations}.</p>
 */
public interface RegulatoryRegistrationRepository
        extends ListCrudRepository<RegulatoryRegistration, UUID> {

    /**
     * Returns a profile's authorisations for one market.
     *
     * <p>Spring derives {@code WHERE host_legal_profile_id = ? AND market_code = ?}, including rows
     * marked {@code NOT_REQUIRED} — which is the point of recording that state explicitly, since it
     * distinguishes "no permit needed here" from "nobody has checked".</p>
     *
     * @param hostLegalProfileId legal profile in question
     * @param marketCode market whose rules apply
     * @return possibly empty list of registrations
     */
    List<RegulatoryRegistration> findAllByHostLegalProfileIdAndMarketCode(
            UUID hostLegalProfileId,
            String marketCode);

    /**
     * Returns verified authorisations that have lapsed or are about to.
     *
     * <pre>{@code
     * SELECT *
     * FROM regulatory_registrations
     * WHERE status = 'VERIFIED'
     *   AND valid_until IS NOT NULL
     *   AND valid_until <= :throughDate
     * ORDER BY valid_until
     * }</pre>
     *
     * <p>{@code valid_until} is a civil date set by an authority, not an instant, so the caller
     * resolves the date it cares about in the market's own zone before calling. A lapsed permit has
     * to withdraw publication, so finding these before they expire is the useful case.</p>
     *
     * @param throughDate civil date to look up to, inclusive
     * @return possibly empty list of expiring registrations, earliest first
     */
    @Query("""
            SELECT *
            FROM regulatory_registrations
            WHERE status = 'VERIFIED'
              AND valid_until IS NOT NULL
              AND valid_until <= :throughDate
            ORDER BY valid_until
            """)
    List<RegulatoryRegistration> findExpiringThrough(@Param("throughDate") LocalDate throughDate);
}
