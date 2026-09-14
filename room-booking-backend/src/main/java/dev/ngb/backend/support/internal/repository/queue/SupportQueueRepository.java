package dev.ngb.backend.support.internal.repository.queue;

import dev.ngb.backend.support.internal.model.queue.SupportQueue;
import dev.ngb.backend.support.internal.model.queue.SupportQueueStatus;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the queue definitions routing evaluates against.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code support_queues}.</p>
 */
public interface SupportQueueRepository extends ListCrudRepository<SupportQueue, UUID> {

    /**
     * Finds one queue by its stable key.
     *
     * @param queueKey queue key
     * @return the queue, when it exists
     */
    Optional<SupportQueue> findByQueueKey(String queueKey);

    /**
     * Lists the queues of a market in one status, for routing and for the operations view.
     *
     * @param marketId market
     * @param status queue status
     * @return possibly empty list
     */
    List<SupportQueue> findByMarketIdAndStatus(UUID marketId, SupportQueueStatus status);
}
