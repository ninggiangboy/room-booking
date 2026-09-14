package dev.ngb.backend.analytics.internal.repository.arrival;

import dev.ngb.backend.analytics.internal.model.arrival.EventDefinitionConsumer;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.arrival.EventDefinitionConsumer;


/**
 * Reads who declares themselves a reader of an event contract.
 *
 * <p>The list is what lets retirement be refused while somebody is still reading, rather than
 * discovered by a pipeline failing on a Tuesday morning.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code event_definition_consumers}.</p>
 */
public interface EventDefinitionConsumerRepository extends ListCrudRepository<EventDefinitionConsumer, UUID> {

    /**
     * Lists the consumers still reading one contract.
     *
     * @param eventDefinitionId the contract
     * @return possibly empty list of consumers that have not withdrawn
     */
    List<EventDefinitionConsumer> findByEventDefinitionIdAndWithdrawnAtIsNull(
            UUID eventDefinitionId);

    /**
     * Lists every contract one consumer reads.
     *
     * @param consumerName the consuming component
     * @return possibly empty list of declarations
     */
    List<EventDefinitionConsumer> findByConsumerNameAndWithdrawnAtIsNull(String consumerName);

    /**
     * Finds one declaration.
     *
     * @param eventDefinitionId the contract
     * @param consumerName the consuming component
     * @param contractVersion the version it reads under
     * @return the declaration, when it exists
     */
    Optional<EventDefinitionConsumer> findByEventDefinitionIdAndConsumerNameAndContractVersion(
            UUID eventDefinitionId, String consumerName, String contractVersion);
}
