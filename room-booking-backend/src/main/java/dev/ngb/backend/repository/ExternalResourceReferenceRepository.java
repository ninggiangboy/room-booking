package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExternalReferenceLifecycle;
import dev.ngb.backend.model.ExternalResourceReference;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves provider resources to platform aggregates and back.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code external_resource_references}.</p>
 */
public interface ExternalResourceReferenceRepository
        extends ListCrudRepository<ExternalResourceReference, UUID> {

    /**
     * Resolves an inbound provider identifier to the aggregate it represents.
     *
     * <p>Spring derives three equality predicates matching the
     * {@code uk_external_resource_references_external} unique constraint:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM external_resource_references
     * WHERE provider_account_key = ?
     *   AND resource_type = ?
     *   AND external_id = ?
     * }</pre>
     *
     * <p>Detached and superseded rows are matched deliberately: a late callback naming an old
     * external identifier must still resolve to something rather than being dropped.</p>
     *
     * @param providerAccountKey provider account the identifier is meaningful within
     * @param resourceType kind of resource held at the provider
     * @param externalId provider-assigned identifier
     * @return the reference when the provider resource is known
     */
    Optional<ExternalResourceReference> findByProviderAccountKeyAndResourceTypeAndExternalId(
            String providerAccountKey,
            String resourceType,
            String externalId);

    /**
     * Finds the live link for one aggregate at one provider.
     *
     * <p>Spring derives five equality predicates. The
     * {@code uk_external_resource_references_active_internal} partial unique index guarantees at
     * most one {@code ACTIVE} row matches, which is what stops a retried submission from creating a
     * second provider resource that nothing reconciles.</p>
     *
     * @param providerAccountKey provider account
     * @param resourceType kind of resource held at the provider
     * @param internalAggregateType kind of platform aggregate
     * @param internalAggregateId identifier of that aggregate
     * @param lifecycleState pass {@link ExternalReferenceLifecycle#ACTIVE} for the live link
     * @return the matching reference when one exists
     */
    Optional<ExternalResourceReference>
            findByProviderAccountKeyAndResourceTypeAndInternalAggregateTypeAndInternalAggregateIdAndLifecycleState(
                    String providerAccountKey,
                    String resourceType,
                    String internalAggregateType,
                    UUID internalAggregateId,
                    ExternalReferenceLifecycle lifecycleState);

    /**
     * Returns every provider link recorded for one aggregate, live or historical.
     *
     * <p>Spring derives {@code WHERE internal_aggregate_type = ? AND internal_aggregate_id = ?}.
     * Used when reconciling an aggregate against several providers at once.</p>
     *
     * @param internalAggregateType kind of platform aggregate
     * @param internalAggregateId identifier of that aggregate
     * @return possibly empty list of references
     */
    List<ExternalResourceReference> findAllByInternalAggregateTypeAndInternalAggregateId(
            String internalAggregateType,
            UUID internalAggregateId);
}
