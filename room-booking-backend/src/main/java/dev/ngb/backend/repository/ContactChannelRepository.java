package dev.ngb.backend.repository;

import dev.ngb.backend.model.ContactChannel;
import dev.ngb.backend.model.ContactChannelType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads how a principal can be reached, and what has been proven about it.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code contact_channels}. Replaced channels are superseded rather than deleted,
 * so the trail of where a notification was actually delivered survives.</p>
 */
public interface ContactChannelRepository extends ListCrudRepository<ContactChannel, UUID> {

    /**
     * Finds the account that has proven ownership of an address.
     *
     * <pre>{@code
     * SELECT *
     * FROM contact_channels
     * WHERE channel_type = :channelType
     *   AND normalized_value = :normalizedValue
     *   AND verified_at IS NOT NULL
     *   AND is_primary = true
     * }</pre>
     *
     * <p>{@code uk_contact_channels_verified_primary_value} guarantees at most one row matches.
     * Unverified claims are deliberately excluded: two people may each register the same address,
     * and only proof confers the exclusive right to it. The column is {@code CITEXT}, so the
     * comparison is case-insensitive in the database.</p>
     *
     * @param channelType kind of channel
     * @param normalizedValue canonical form of the address
     * @return the verified primary channel when one exists
     */
    @Query("""
            SELECT *
            FROM contact_channels
            WHERE channel_type = :channelType
              AND normalized_value = :normalizedValue
              AND verified_at IS NOT NULL
              AND is_primary = true
            """)
    Optional<ContactChannel> findVerifiedPrimaryByValue(
            @Param("channelType") String channelType,
            @Param("normalizedValue") String normalizedValue);

    /**
     * Finds a principal's current channel of record for one type.
     *
     * <pre>{@code
     * SELECT *
     * FROM contact_channels
     * WHERE user_id = :userId
     *   AND channel_type = :channelType
     *   AND is_primary = true
     *   AND superseded_by IS NULL
     * }</pre>
     *
     * <p>{@code uk_contact_channels_one_primary} guarantees at most one row matches. This is where a
     * security notification must be delivered, regardless of what other channels exist.</p>
     *
     * @param userId principal being contacted
     * @param channelType kind of channel required
     * @return the current primary channel when one exists
     */
    @Query("""
            SELECT *
            FROM contact_channels
            WHERE user_id = :userId
              AND channel_type = :channelType
              AND is_primary = true
              AND superseded_by IS NULL
            """)
    Optional<ContactChannel> findCurrentPrimary(
            @Param("userId") UUID userId,
            @Param("channelType") String channelType);

    /**
     * Returns every channel of one type a principal has registered.
     *
     * <p>Spring derives {@code WHERE user_id = ? AND channel_type = ?}, including unverified and
     * superseded rows.</p>
     *
     * @param userId principal whose channels are listed
     * @param channelType kind of channel
     * @return possibly empty list of channels
     */
    List<ContactChannel> findAllByUserIdAndChannelType(UUID userId, ContactChannelType channelType);
}
