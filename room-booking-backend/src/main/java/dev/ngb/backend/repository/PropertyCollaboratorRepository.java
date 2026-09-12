package dev.ngb.backend.repository;

import dev.ngb.backend.model.CollaboratorStatus;
import dev.ngb.backend.model.PropertyCollaborator;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who works on a property they do not own.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code property_collaborators}. Nothing here decides what a collaborator may do;
 * that is asked of the property-scoped capability grant the collaboration points at.</p>
 */
public interface PropertyCollaboratorRepository
        extends ListCrudRepository<PropertyCollaborator, UUID> {

    /**
     * Finds one person's collaboration on one property.
     *
     * <p>Spring derives {@code WHERE property_id = ? AND user_id = ?}, matching
     * {@code uk_property_collaborators_pair}. Returns ended collaborations too, so past actions stay
     * attributable.</p>
     *
     * @param propertyId property in question
     * @param userId person in question
     * @return the collaboration when one exists, in any status
     */
    Optional<PropertyCollaborator> findByPropertyIdAndUserId(UUID propertyId, UUID userId);

    /**
     * Returns a property's collaborators in one status.
     *
     * <p>Spring derives {@code WHERE property_id = ? AND status = ?}.</p>
     *
     * @param propertyId property whose collaborators are listed
     * @param status status to filter by
     * @return possibly empty list of collaborations
     */
    List<PropertyCollaborator> findAllByPropertyIdAndStatus(
            UUID propertyId,
            CollaboratorStatus status);

    /**
     * Returns the properties one person collaborates on, in one status.
     *
     * <p>Spring derives {@code WHERE user_id = ? AND status = ?}. Backs the co-host's own view of
     * what they are responsible for.</p>
     *
     * @param userId person whose collaborations are listed
     * @param status status to filter by
     * @return possibly empty list of collaborations
     */
    List<PropertyCollaborator> findAllByUserIdAndStatus(UUID userId, CollaboratorStatus status);
}
