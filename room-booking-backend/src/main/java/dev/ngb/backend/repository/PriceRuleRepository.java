package dev.ngb.backend.repository;

import dev.ngb.backend.model.PriceRule;
import dev.ngb.backend.model.PriceRuleScope;
import dev.ngb.backend.model.PriceRuleStatus;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the identities of pricing rules.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code price_rules}.</p>
 *
 * <p>A rule alone prices nothing. The engine resolves candidates here and then loads the version in
 * force through {@link PriceRuleVersionRepository}, because only a published version may be cited by
 * a priced night.</p>
 */
public interface PriceRuleRepository extends ListCrudRepository<PriceRule, UUID> {

    /**
     * Returns the rules attached directly to one accommodation type.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ? AND status = ? ORDER BY priority ASC}.
     * Priority ascending is the order the engine applies them in.</p>
     *
     * @param accommodationTypeId accommodation type being priced
     * @param status status to include, normally {@link PriceRuleStatus#ACTIVE}
     * @return possibly empty list, lowest priority number first
     */
    List<PriceRule> findAllByAccommodationTypeIdAndStatusOrderByPriorityAsc(
            UUID accommodationTypeId, PriceRuleStatus status);

    /**
     * Returns the rules a host has defined across their portfolio.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ? AND status = ? ORDER BY priority ASC}.</p>
     *
     * @param accountHolderId host whose rules are wanted
     * @param status status to include
     * @return possibly empty list, lowest priority number first
     */
    List<PriceRule> findAllByAccountHolderIdAndStatusOrderByPriorityAsc(
            UUID accountHolderId, PriceRuleStatus status);

    /**
     * Returns the rules at one scope, for platform and market layers.
     *
     * <p>Spring derives {@code WHERE scope = ? AND status = ? ORDER BY priority ASC}.</p>
     *
     * @param scope scope layer being resolved
     * @param status status to include
     * @return possibly empty list, lowest priority number first
     */
    List<PriceRule> findAllByScopeAndStatusOrderByPriorityAsc(
            PriceRuleScope scope, PriceRuleStatus status);
}
