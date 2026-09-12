package dev.ngb.backend.repository;

import dev.ngb.backend.model.PropertySafetyItem;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what safety equipment a property declares.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code property_safety_items}.</p>
 */
public interface PropertySafetyItemRepository extends ListCrudRepository<PropertySafetyItem, UUID> {

    /**
     * Returns every safety declaration for a property.
     *
     * <p>Spring derives {@code WHERE property_id = ?}. Items declared absent are included: "this
     * property has no smoke alarm" is information a guest is entitled to before booking, and
     * filtering it out would present the same page as a property nobody has asked.</p>
     *
     * @param propertyId property whose declarations are listed
     * @return possibly empty list of declarations
     */
    List<PropertySafetyItem> findAllByPropertyId(UUID propertyId);

    /**
     * Finds one safety declaration.
     *
     * <p>Spring derives {@code WHERE property_id = ? AND safety_item_key = ?}, matching
     * {@code uk_property_safety_items}. An empty result means nobody has asked about this item, which
     * is not the same as it being absent.</p>
     *
     * @param propertyId property in question
     * @param safetyItemKey stable key of the item
     * @return the declaration when one has been made
     */
    Optional<PropertySafetyItem> findByPropertyIdAndSafetyItemKey(
            UUID propertyId,
            String safetyItemKey);
}
