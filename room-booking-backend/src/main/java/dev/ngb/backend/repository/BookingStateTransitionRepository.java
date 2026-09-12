package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingStateDimension;
import dev.ngb.backend.model.BookingStateTransition;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the recorded movements of a booking's state.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code booking_state_transitions}. The inherited update and delete methods will fail at
 * runtime: the table is append-only and a database trigger refuses both. A mistaken transition is
 * answered with a compensating one.</p>
 */
public interface BookingStateTransitionRepository
        extends ListCrudRepository<BookingStateTransition, UUID> {

    /**
     * Returns a booking's whole history, newest first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY sequence_number DESC}.</p>
     *
     * @param bookingId booking whose history is wanted
     * @return possibly empty list of transitions
     */
    List<BookingStateTransition> findAllByBookingIdOrderBySequenceNumberDesc(UUID bookingId);

    /**
     * Returns the history of one dimension of a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND dimension = ? ORDER BY sequence_number}.
     * Used to answer a question about one machine — how the payment progressed, say — without
     * reading every unrelated move interleaved with it.</p>
     *
     * @param bookingId booking whose history is wanted
     * @param dimension dimension to filter on
     * @return possibly empty list of transitions, oldest first
     */
    List<BookingStateTransition> findAllByBookingIdAndDimensionOrderBySequenceNumber(
            UUID bookingId, BookingStateDimension dimension);

    /**
     * Returns the highest sequence number recorded for a booking.
     *
     * <pre>{@code
     * SELECT max(sequence_number)
     * FROM booking_state_transitions
     * WHERE booking_id = :bookingId
     * }</pre>
     *
     * <p>Empty for a booking with no history yet, so the first transition is number one.</p>
     *
     * <p>Read this only while holding the booking row lock. Two writers that read the same maximum
     * will compute the same next number, and {@code uk_booking_state_transitions_sequence} will
     * reject the loser — a safe failure, but one the lock avoids paying for.</p>
     *
     * @param bookingId booking whose history is being appended to
     * @return the current highest sequence number, when any transition exists
     */
    @Query("""
            SELECT max(sequence_number)
            FROM booking_state_transitions
            WHERE booking_id = :bookingId
            """)
    Optional<Long> findHighestSequenceNumber(@Param("bookingId") UUID bookingId);

    /**
     * Finds a transition already recorded for a command.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND command_id = ?}. Lets a retried command
     * recognise that its work is already recorded rather than appending the same move twice.</p>
     *
     * @param bookingId booking the command targeted
     * @param commandId command identifier the caller supplied
     * @return the transition that command already produced, when one exists
     */
    List<BookingStateTransition> findAllByBookingIdAndCommandId(UUID bookingId, String commandId);
}
