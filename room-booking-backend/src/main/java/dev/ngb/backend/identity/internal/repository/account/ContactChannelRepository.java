package dev.ngb.backend.identity.internal.repository.account;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;


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
     * WHERE account_holder_id = :accountHolderId
     *   AND channel_type = :channelType
     *   AND is_primary = true
     *   AND superseded_by IS NULL
     * }</pre>
     *
     * <p>{@code uk_contact_channels_one_primary} guarantees at most one row matches. This is where a
     * security notification must be delivered, regardless of what other channels exist.</p>
     *
     * @param accountHolderId principal being contacted
     * @param channelType kind of channel required
     * @return the current primary channel when one exists
     */
    @Query("""
            SELECT *
            FROM contact_channels
            WHERE account_holder_id = :accountHolderId
              AND channel_type = :channelType
              AND is_primary = true
              AND superseded_by IS NULL
            """)
    Optional<ContactChannel> findCurrentPrimary(
            @Param("accountHolderId") UUID accountHolderId,
            @Param("channelType") String channelType);

    /**
     * Returns every channel of one type a principal has registered.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ? AND channel_type = ?}, including
     * unverified and superseded rows.</p>
     *
     * @param accountHolderId principal whose channels are listed
     * @param channelType kind of channel
     * @return possibly empty list of channels
     */
    List<ContactChannel> findAllByAccountHolderIdAndChannelType(
            UUID accountHolderId, ContactChannelType channelType);

    /**
     * Returns every current primary channel claiming one address, verified or not.
     *
     * <p>Spring derives {@code WHERE channel_type = ? AND normalized_value = ? AND is_primary =
     * true}. Deliberately not narrowed to a single result: two accounts may each claim the same
     * address before either proves it, per {@link ContactChannel}'s class documentation, so login
     * resolution ({@link dev.ngb.backend.identity.internal.service.account.AccountHolderFinder})
     * must be prepared for more than one candidate.</p>
     *
     * @param channelType kind of channel
     * @param normalizedValue canonical form of the address
     * @return possibly empty, possibly multi-row list of claiming channels
     */
    List<ContactChannel> findAllByChannelTypeAndNormalizedValueAndIsPrimaryTrue(
            ContactChannelType channelType, String normalizedValue);

    /**
     * Performs an existence check for one address, verified or not.
     *
     * <p>Spring derives {@code SELECT ... WHERE channel_type = ? AND normalized_value = ?}. Used to
     * reject a duplicate registration attempt before any row is written, the same guard
     * {@code UserRepository.existsByEmail} used to provide against {@code users.email}.</p>
     *
     * @param channelType kind of channel
     * @param normalizedValue canonical form of the address
     * @return whether any channel, verified or not, already claims that address
     */
    boolean existsByChannelTypeAndNormalizedValue(
            ContactChannelType channelType, String normalizedValue);
}
