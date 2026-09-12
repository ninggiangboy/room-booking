package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostStatement;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the statements hosts are shown.
 *
 * <p>An issued statement is refused any change by the database, so there is no amend path here. A
 * correction is a new version that links back to the one it replaces.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_statements}.</p>
 */
public interface HostStatementRepository extends ListCrudRepository<HostStatement, UUID> {

    /**
     * Finds a statement by the identifier a host or an agent would quote.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching {@code uk_host_statements_public_id}.</p>
     *
     * @param publicId short public identifier
     * @return the statement, when one exists
     */
    Optional<HostStatement> findByPublicId(String publicId);

    /**
     * Returns a host's statements in one currency, most recent period first.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_statements
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND currency = :currency
     * ORDER BY lower(period_range) DESC, statement_version DESC
     * }</pre>
     *
     * <p>Matches {@code idx_host_statements_host}. Superseded versions are returned as well as current
     * ones: what the host was told before is part of the record, not something to hide.</p>
     *
     * @param hostAccountHolderId host whose statements are wanted
     * @param currency ISO 4217 code
     * @return possibly empty list of statements
     */
    @Query("""
            SELECT *
            FROM host_statements
            WHERE host_account_holder_id = :hostAccountHolderId
              AND currency = :currency
            ORDER BY lower(period_range) DESC, statement_version DESC
            """)
    List<HostStatement> findForHost(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("currency") String currency);

    /**
     * Returns the statement accompanying a payout, when one was produced.
     *
     * <p>Spring derives {@code WHERE payout_instruction_id = ?}, matching
     * {@code idx_host_statements_payout}. Several rows can exist when a statement has been corrected, so
     * this returns a list rather than an optional.</p>
     *
     * @param payoutInstructionId payout whose statements are wanted
     * @return possibly empty list of statements
     */
    List<HostStatement> findAllByPayoutInstructionId(UUID payoutInstructionId);
}
