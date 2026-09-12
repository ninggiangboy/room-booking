package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostStatementLine;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the lines a statement is composed from.
 *
 * <p>Lines of an issued statement cannot be added to, altered, or removed, so composition happens while
 * the statement is still a draft.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_statement_lines}.</p>
 */
public interface HostStatementLineRepository extends ListCrudRepository<HostStatementLine, UUID> {

    /**
     * Returns a statement's lines in order.
     *
     * <p>Spring derives {@code WHERE statement_id = ? ORDER BY line_number}, matching
     * {@code uk_host_statement_lines_number}.</p>
     *
     * @param statementId statement whose lines are wanted
     * @return possibly empty list of lines, in presentation order
     */
    List<HostStatementLine> findAllByStatementIdOrderByLineNumber(UUID statementId);

    /**
     * Returns the statement lines that mention a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at}, matching
     * {@code idx_host_statement_lines_booking}. This answers a host asking what a particular stay
     * actually earned them, across however many statements it touched.</p>
     *
     * @param bookingId booking whose statement lines are wanted
     * @return possibly empty list of lines, oldest first
     */
    List<HostStatementLine> findAllByBookingIdOrderByCreatedAt(UUID bookingId);
}
