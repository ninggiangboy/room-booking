package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccommodationType;
import dev.ngb.backend.model.SupplyLifecycle;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the sellable categories at a property.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code accommodation_types}. This is the inventory authority: a listing presents
 * a category, but only the category and its calendar decide what can be sold.</p>
 */
public interface AccommodationTypeRepository extends ListCrudRepository<AccommodationType, UUID> {

    /**
     * Finds an accommodation type by its operational reference.
     *
     * <p>Spring derives {@code WHERE reference_code = ?}, matching
     * {@code uk_accommodation_types_reference}.</p>
     *
     * @param referenceCode operational reference
     * @return the accommodation type when the reference is known
     */
    Optional<AccommodationType> findByReferenceCode(String referenceCode);

    /**
     * Returns a property's categories in one lifecycle state.
     *
     * <p>Spring derives {@code WHERE property_id = ? AND lifecycle_state = ?}.</p>
     *
     * @param propertyId property whose categories are listed
     * @param lifecycleState state to filter by
     * @return possibly empty list of accommodation types
     */
    List<AccommodationType> findAllByPropertyIdAndLifecycleState(
            UUID propertyId,
            SupplyLifecycle lifecycleState);

    /**
     * Loads an accommodation type and locks it for the duration of the transaction.
     *
     * <pre>{@code
     * SELECT * FROM accommodation_types WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Changing an accommodation type's
     * inventory mode or sellable quantity while a booking is being committed against it would let the
     * commit validate against one shape of inventory and write against another. Locking the row
     * serializes the two.</p>
     *
     * @param id accommodation type to lock
     * @return the locked row when it exists
     */
    @Query("SELECT * FROM accommodation_types WHERE id = :id FOR UPDATE")
    Optional<AccommodationType> findByIdForUpdate(@Param("id") UUID id);
}
