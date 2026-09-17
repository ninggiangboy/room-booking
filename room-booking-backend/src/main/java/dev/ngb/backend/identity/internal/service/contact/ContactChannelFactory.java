package dev.ngb.backend.identity.internal.service.contact;

import java.util.UUID;
import org.springframework.stereotype.Component;

import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;

/**
 * Constructs a new, unverified contact channel row.
 *
 * <p>{@code @Component} makes the factory injectable; it needs no collaborators today, unlike
 * {@code UserRegistrationFactory}'s encoder, but still exists as its own type so this workflow
 * never assembles the entity with an inline builder at its call site. Package-private visibility
 * matches every other factory in this codebase.</p>
 *
 * <p>Whether the new row becomes primary is decided by the caller, not computed here: answering
 * "does this holder already have a live primary channel of this type" requires a repository read,
 * which stays out of a factory by convention (see
 * {@code docs/conventions/03-entities-and-persistence.md}).</p>
 */
@Component
class ContactChannelFactory {

    /**
     * Builds an unsaved, unverified channel.
     *
     * @param accountHolderId owner of the new channel
     * @param channelType kind of channel being added
     * @param purpose what the channel will be used for
     * @param rawValue value exactly as the caller submitted it
     * @param normalizedValue canonical form used for comparison and uniqueness
     * @param isPrimary whether this channel becomes the holder's channel of record for its type
     * @return unsaved channel ready to persist
     */
    ContactChannel create(
            UUID accountHolderId,
            ContactChannelType channelType,
            ContactChannelPurpose purpose,
            String rawValue,
            String normalizedValue,
            boolean isPrimary) {
        return ContactChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(accountHolderId)
                .channelType(channelType)
                .normalizedValue(normalizedValue)
                .originalValue(rawValue)
                .purpose(purpose)
                .isPrimary(isPrimary)
                .build();
    }
}
