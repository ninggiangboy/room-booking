package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccountHolder;
import dev.ngb.backend.model.AccountHolderType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and stores the entities that own supply, contract, and are settled.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code account_holders}. Nothing here deletes a holder in practice: closure is
 * a status, because a closed holder still has to explain the bookings it contracted.</p>
 */
public interface AccountHolderRepository extends ListCrudRepository<AccountHolder, UUID> {

    /**
     * Finds the person holder backing one user account.
     *
     * <p>Spring derives two equality predicates from the property path:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM account_holders
     * WHERE user_id = ?
     *   AND holder_type = ?
     * }</pre>
     *
     * <p>{@code uk_account_holders_person_user} guarantees at most one person holder per user, so
     * {@link Optional} is the honest return type.</p>
     *
     * @param userId user whose holder is wanted
     * @param holderType pass {@link AccountHolderType#PERSON}
     * @return the holder when it exists
     */
    Optional<AccountHolder> findByUserIdAndHolderType(UUID userId, AccountHolderType holderType);

    /**
     * Returns holders whose market context still has to be resolved by an operator.
     *
     * <p>Spring derives {@code WHERE context_state = 'LEGACY_UNRECONCILED'} from the enum argument
     * and orders by creation. These are rows backfilled from before markets existed; they are
     * blocked from consequential workflows until a market is recorded, because guessing one would
     * let a contract form under rules nobody approved.</p>
     *
     * @param contextState pass the unreconciled state
     * @return possibly empty list of holders awaiting reconciliation, oldest first
     */
    List<AccountHolder> findAllByContextStateOrderByCreatedAtAsc(
            dev.ngb.backend.model.MarketContextState contextState);
}
