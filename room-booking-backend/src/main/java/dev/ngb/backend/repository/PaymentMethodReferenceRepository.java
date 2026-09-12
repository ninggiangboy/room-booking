package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentMethodReference;
import dev.ngb.backend.model.PaymentMethodState;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the opaque instrument tokens a guest has stored.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_method_references}.</p>
 */
public interface PaymentMethodReferenceRepository extends ListCrudRepository<PaymentMethodReference, UUID> {

    /**
     * Returns a guest's usable instruments, newest first.
     *
     * <p>Spring derives
     * {@code WHERE owner_account_holder_id = ? AND state = ? ORDER BY created_at DESC}. Callers pass
     * {@code ACTIVE}: a revoked reference is kept so historic transactions stay explainable, but it
     * must never be offered as a way to pay.</p>
     *
     * @param ownerAccountHolderId guest whose instruments are wanted
     * @param state usability to filter on
     * @return possibly empty list of references
     */
    List<PaymentMethodReference> findAllByOwnerAccountHolderIdAndStateOrderByCreatedAtDesc(
            UUID ownerAccountHolderId, PaymentMethodState state);

    /**
     * Finds the reference behind one provider token.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_token = ?}, matching
     * {@code uk_payment_method_references_token}. A token is scoped to one merchant account and is not
     * portable, which is why the account is part of the lookup.</p>
     *
     * @param providerAccountId merchant account the token belongs to
     * @param providerToken opaque provider token
     * @return the reference, when one exists
     */
    Optional<PaymentMethodReference> findByProviderAccountIdAndProviderToken(
            UUID providerAccountId, String providerToken);
}
