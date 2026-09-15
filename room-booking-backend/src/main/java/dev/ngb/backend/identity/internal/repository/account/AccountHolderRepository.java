package dev.ngb.backend.identity.internal.repository.account;

import dev.ngb.backend.identity.internal.model.account.MarketContextState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.AccountHolder;


/**
 * Reads and stores the entities that own supply, contract, and are settled.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code account_holders}. Nothing here deletes a holder in practice: closure is
 * a status, because a closed holder still has to explain the bookings it contracted. Since migration
 * {@code 037} retired the legacy {@code users} table, {@code AccountHolder.getId()} is also the
 * identifier every JWT subject claim names, so a lookup by that id is simply {@code findById},
 * inherited from {@code ListCrudRepository}.</p>
 */
public interface AccountHolderRepository extends ListCrudRepository<AccountHolder, UUID> {

    /**
     * Loads one holder while taking a transaction-scoped row lock.
     *
     * <pre>{@code
     * SELECT * FROM account_holders WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>{@code id} is bound to the named parameter. The lock serializes concurrent status or
     * profile changes for the same holder until the surrounding transaction ends.</p>
     *
     * @param id holder identifier
     * @return optional locked holder, empty when no row matches
     */
    @Query("SELECT * FROM account_holders WHERE id = :id FOR UPDATE")
    Optional<AccountHolder> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns holders whose market context has never been resolved by an operator.
     *
     * <p>Spring derives {@code WHERE context_state = 'UNRESOLVED'} from the enum argument and
     * orders by creation. These holders are blocked from consequential workflows until a market is
     * recorded, because guessing one would let a contract form under rules nobody approved.</p>
     *
     * @param contextState pass the unresolved state
     * @return possibly empty list of holders awaiting resolution, oldest first
     */
    List<AccountHolder> findAllByContextStateOrderByCreatedAtAsc(
            MarketContextState contextState);
}
