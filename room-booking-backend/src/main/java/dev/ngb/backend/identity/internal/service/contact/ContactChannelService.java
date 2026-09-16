package dev.ngb.backend.identity.internal.service.contact;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

import dev.ngb.backend.identity.internal.exception.ContactChannelAlreadyRegisteredException;
import dev.ngb.backend.identity.internal.exception.ContactChannelAlreadyVerifiedException;
import dev.ngb.backend.identity.internal.exception.ContactChannelNotFoundException;
import dev.ngb.backend.identity.internal.exception.InvalidContactChannelValueException;
import dev.ngb.backend.identity.internal.exception.InvalidContactChannelVerificationCodeException;
import dev.ngb.backend.identity.internal.exception.PrimaryContactChannelException;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.platform.util.HashUtils;
import dev.ngb.backend.platform.util.StringUtils;

/**
 * Lets a holder manage contact channels beyond the primary email registration creates: adding one,
 * proving control of it, listing the current set, and removing one.
 *
 * <p>{@code @Service} identifies business logic; Lombok generates constructor injection for the
 * final dependencies. Public entry points are transactional so channel creation, code issuance and
 * consumption, and removal each commit or roll back as one unit — the same discipline
 * {@code EmailVerificationService} and {@code UserAccountService} already follow.</p>
 */
@Service
@RequiredArgsConstructor
public class ContactChannelService {

    private static final String VERIFICATION_METHOD_PHONE = "SMS_CODE";
    private static final String VERIFICATION_METHOD_EMAIL = "EMAIL_CODE";
    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AccountHolderFinder accountHolderFinder;
    private final ContactChannelRepository contactChannelRepository;
    private final ContactChannelFactory contactChannelFactory;
    private final AuthTokenRepository authTokenRepository;
    private final AuthTokenFactory authTokenFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.contact-channel-verification.code-ttl:15m}")
    private Duration codeTtl;

    /** Ensures invalid TTL configuration fails during application startup. */
    @PostConstruct
    void validateConfiguration() {
        if (codeTtl.isNegative() || codeTtl.isZero()) {
            throw new IllegalStateException("contact channel verification code TTL must be positive");
        }
    }

    /**
     * Lists every live channel a holder has registered, in no particular order.
     *
     * @param holderId authenticated account
     * @return possibly empty list of the holder's current channels
     */
    @Transactional(readOnly = true)
    public List<ContactChannel> list(UUID holderId) {
        return contactChannelRepository.findAllByAccountHolderIdAndSupersededByIsNullAndRevokedAtIsNull(
                holderId);
    }

    /**
     * Registers a new, unverified channel for the holder.
     *
     * <p>Becomes the holder's primary channel for its type only when the holder has no live
     * primary of that type yet — true today only for a first {@code PHONE} channel, since
     * registration always creates a primary {@code EMAIL} channel already.</p>
     *
     * @param holderId authenticated account
     * @param channelType kind of channel being added
     * @param purpose what the channel will be used for
     * @param rawValue value exactly as the caller submitted it
     * @return the newly created, unverified channel
     * @throws InvalidContactChannelValueException when the value does not match the shape its
     *     declared type requires
     * @throws ContactChannelAlreadyRegisteredException when the holder already has a live channel
     *     of this exact type and value
     */
    @Transactional
    public ContactChannel add(
            UUID holderId, ContactChannelType channelType, ContactChannelPurpose purpose,
            String rawValue) {
        accountHolderFinder.findActiveById(holderId);
        String normalizedValue = normalize(channelType, rawValue);
        requireValidShape(channelType, normalizedValue);

        boolean alreadyHasThisValue = contactChannelRepository
                .findAllByAccountHolderIdAndSupersededByIsNullAndRevokedAtIsNull(holderId)
                .stream()
                .anyMatch(channel -> channel.getChannelType() == channelType
                        && channel.getNormalizedValue().equals(normalizedValue));
        if (alreadyHasThisValue) {
            throw new ContactChannelAlreadyRegisteredException(channelType, normalizedValue);
        }

        boolean hasLivePrimaryOfType = contactChannelRepository
                .findCurrentPrimary(holderId, channelType.name())
                .isPresent();

        ContactChannel channel = contactChannelFactory.create(
                holderId, channelType, purpose, rawValue, normalizedValue, !hasLivePrimaryOfType);
        return contactChannelRepository.save(channel);
    }

    /**
     * Issues a numeric verification code for one of the holder's own channels and delivers it
     * through the transport matching the channel's type.
     *
     * @param holderId authenticated account
     * @param channelId channel to verify; must belong to the caller
     * @throws ContactChannelNotFoundException when the channel does not exist or belongs to
     *     another holder
     * @throws ContactChannelAlreadyVerifiedException when the channel is already verified
     */
    @Transactional
    public void requestVerification(UUID holderId, UUID channelId) {
        ContactChannel channel = findOwnChannel(holderId, channelId);
        if (channel.isVerified()) {
            throw new ContactChannelAlreadyVerifiedException(channelId);
        }

        Instant now = clock.instant();
        // A newly issued code supersedes every earlier one still outstanding for this channel.
        authTokenRepository.findAllByChannelIdAndConsumedAtIsNull(channelId).forEach(token -> {
            token.consume(now, TokenConsumptionReason.ROTATED);
            authTokenRepository.save(token);
        });

        AuthTokenFactory.IssuedToken issued = authTokenFactory.createChannelVerificationCode(
                holderId, channelId, now, codeTtl);
        authTokenRepository.save(issued.token());
        eventPublisher.publishEvent(new ContactChannelVerificationIssued(
                channel.getChannelType(), channel.getNormalizedValue(), issued.rawToken()));
    }

    /**
     * Confirms a code and marks the corresponding channel verified.
     *
     * @param holderId authenticated account
     * @param channelId channel the code was requested for
     * @param rawCode code as the holder typed it
     * @return the channel after verification
     * @throws ContactChannelNotFoundException when the channel does not exist or belongs to
     *     another holder
     * @throws ContactChannelAlreadyVerifiedException when the channel is already verified
     * @throws InvalidContactChannelVerificationCodeException when the code is unknown, expired,
     *     consumed, or was issued for a different channel
     */
    @Transactional
    public ContactChannel confirmVerification(UUID holderId, UUID channelId, String rawCode) {
        ContactChannel channel = findOwnChannel(holderId, channelId);
        if (channel.isVerified()) {
            throw new ContactChannelAlreadyVerifiedException(channelId);
        }

        AuthToken token = authTokenRepository
                .findByTokenHashAndType(
                        HashUtils.sha256Hex(rawCode), AuthTokenType.CONTACT_CHANNEL_VERIFICATION)
                .filter(candidate -> channelId.equals(candidate.getChannelId()))
                .orElseThrow(InvalidContactChannelVerificationCodeException::new);

        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            throw new InvalidContactChannelVerificationCodeException();
        }
        token.consume(now, TokenConsumptionReason.USED);
        authTokenRepository.save(token);

        String method = channel.getChannelType() == ContactChannelType.PHONE
                ? VERIFICATION_METHOD_PHONE
                : VERIFICATION_METHOD_EMAIL;
        channel.markVerified(now, method);
        return contactChannelRepository.save(channel);
    }

    /**
     * Removes one of the holder's own channels through self-service.
     *
     * <p>Sets {@link ContactChannel#markRemoved} rather than deleting the row: a consumed
     * verification token can still reference the channel by foreign key, and a removed channel is
     * evidence of what the holder once claimed reachability through, not a mistake to erase.</p>
     *
     * @param holderId authenticated account
     * @param channelId channel to remove; must belong to the caller
     * @throws ContactChannelNotFoundException when the channel does not exist or belongs to
     *     another holder
     * @throws PrimaryContactChannelException when the channel is currently primary for its type
     */
    @Transactional
    public void remove(UUID holderId, UUID channelId) {
        ContactChannel channel = findOwnChannel(holderId, channelId);
        if (channel.isPrimary()) {
            throw new PrimaryContactChannelException(channelId);
        }
        channel.markRemoved(clock.instant());
        contactChannelRepository.save(channel);
    }

    private ContactChannel findOwnChannel(UUID holderId, UUID channelId) {
        return contactChannelRepository.findById(channelId)
                .filter(candidate -> holderId.equals(candidate.getAccountHolderId()))
                .filter(candidate -> !candidate.isRemoved())
                .orElseThrow(() -> new ContactChannelNotFoundException(channelId));
    }

    private static String normalize(ContactChannelType channelType, String rawValue) {
        return channelType == ContactChannelType.EMAIL
                ? StringUtils.normalizeLowerCase(rawValue)
                : StringUtils.normalizeRequired(rawValue);
    }

    /**
     * Rejects a value that does not match its declared type's shape.
     *
     * <p>{@code PUSH} carries no format check: a push destination is an opaque token assigned by
     * the client platform, not something with a shape this service can validate.</p>
     */
    private static void requireValidShape(ContactChannelType channelType, String normalizedValue) {
        boolean valid = switch (channelType) {
            case PHONE -> E164_PHONE.matcher(normalizedValue).matches();
            case EMAIL -> EMAIL_SHAPE.matcher(normalizedValue).matches();
            case PUSH -> true;
        };
        if (!valid) {
            throw new InvalidContactChannelValueException(channelType);
        }
    }
}
