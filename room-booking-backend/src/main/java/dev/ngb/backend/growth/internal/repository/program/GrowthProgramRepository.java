package dev.ngb.backend.growth.internal.repository.program;

import dev.ngb.backend.growth.internal.model.program.GrowthProgram;
import dev.ngb.backend.growth.internal.model.program.GrowthProgramKind;
import dev.ngb.backend.growth.internal.model.program.GrowthProgramStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.program.GrowthProgram;
import dev.ngb.backend.growth.internal.model.program.GrowthProgramKind;
import dev.ngb.backend.growth.internal.model.program.GrowthProgramStatus;


/**
 * Reads the growth programmes the platform runs and the balance sheet behind each of them.
 *
 * <p>Every credit, reward and commission in this domain traces back to one of these rows, so
 * this is where the question "whose money was that" is answered.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code growth_programs}.</p>
 */
public interface GrowthProgramRepository extends ListCrudRepository<GrowthProgram, UUID> {

    /**
     * Finds a programme by the key it is known by.
     *
     * @param programKey the stable programme key
     * @return the programme, when one is registered
     */
    Optional<GrowthProgram> findByProgramKey(String programKey);

    /**
     * Lists the programmes of one kind in one lifecycle state.
     *
     * @param programKind the kind of mechanism
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<GrowthProgram> findByProgramKindAndStatus(GrowthProgramKind programKind,
            GrowthProgramStatus status);

    /**
     * Lists the programmes open in one market, including those that run everywhere.
     *
     * <pre>{@code
     * SELECT * FROM growth_programs
     * WHERE status = 'ACTIVE' AND (market_code IS NULL OR market_code = :marketCode)
     * ORDER BY program_kind, program_key
     * }</pre>
     *
     * @param marketCode ISO 3166-1 alpha-2 market code
     * @return possibly empty list, by kind then key
     */
    @Query("""
            SELECT * FROM growth_programs
            WHERE status = 'ACTIVE' AND (market_code IS NULL OR market_code = :marketCode)
            ORDER BY program_kind, program_key
            """)
    List<GrowthProgram> findActiveForMarket(@Param("marketCode") String marketCode);

    /**
     * Lists the programmes that hand out value and are drawn against one ledger account, which is
     * the set a reconciliation of that liability has to read.
     *
     * <pre>{@code
     * SELECT * FROM growth_programs
     * WHERE grants_value AND liability_account_id = :liabilityAccountId
     * ORDER BY program_key
     * }</pre>
     *
     * @param liabilityAccountId the ledger liability account
     * @return possibly empty list, by programme key
     */
    @Query("""
            SELECT * FROM growth_programs
            WHERE grants_value AND liability_account_id = :liabilityAccountId
            ORDER BY program_key
            """)
    List<GrowthProgram> findByLiability(@Param("liabilityAccountId") UUID liabilityAccountId);
}
