package dev.ngb.backend.repository;

import dev.ngb.backend.model.PropertyHouseRules;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

/**
 * Reads the rules and stay times a property operates under.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code property_house_rules}. The property is the primary key, so
 * {@code findById} takes a property identifier and a property has exactly one set of rules.</p>
 *
 * <p>The times returned are civil times in the property's own zone. Resolving them to instants
 * requires the property's {@code timeZone} and must go through {@code StayCalendar}; treating them as
 * UTC would shift every check-in by the property's offset.</p>
 */
public interface PropertyHouseRulesRepository extends ListCrudRepository<PropertyHouseRules, UUID> {
}
