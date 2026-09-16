package dev.ngb.backend.identity.internal.service.contact;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ngb.backend.identity.internal.exception.ContactChannelAlreadyRegisteredException;
import dev.ngb.backend.identity.internal.exception.ContactChannelAlreadyVerifiedException;
import dev.ngb.backend.identity.internal.exception.ContactChannelNotFoundException;
import dev.ngb.backend.identity.internal.exception.InvalidContactChannelValueException;
import dev.ngb.backend.identity.internal.exception.InvalidContactChannelVerificationCodeException;
import dev.ngb.backend.identity.internal.exception.PrimaryContactChannelException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelPurpose;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.platform.util.HashUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactChannelServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountHolderFinder accountHolderFinder;
    @Mock
    private ContactChannelRepository contactChannelRepository;
    @Mock
    private ContactChannelFactory contactChannelFactory;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private AuthTokenFactory authTokenFactory;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ContactChannelService service;
    private UUID holderId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ContactChannelService(
                accountHolderFinder, contactChannelRepository, contactChannelFactory,
                authTokenRepository, authTokenFactory, eventPublisher, clock);
        ReflectionTestUtils.setField(service, "codeTtl", Duration.ofMinutes(15));
        holderId = UUID.randomUUID();
    }

    @Test
    void addingAFirstPhoneNumberMakesItPrimary() {
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(activeHolder());
        when(contactChannelRepository.findAllByAccountHolderIdAndSupersededByIsNullAndRevokedAtIsNull(holderId))
                .thenReturn(List.of());
        when(contactChannelRepository.findCurrentPrimary(holderId, ContactChannelType.PHONE.name()))
                .thenReturn(Optional.empty());
        ContactChannel built = channel(ContactChannelType.PHONE, "+84912345678", true);
        when(contactChannelFactory.create(
                holderId, ContactChannelType.PHONE, ContactChannelPurpose.ACCOUNT,
                "+84912345678", "+84912345678", true))
                .thenReturn(built);
        when(contactChannelRepository.save(built)).thenReturn(built);

        ContactChannel result = service.add(
                holderId, ContactChannelType.PHONE, ContactChannelPurpose.ACCOUNT, "+84912345678");

        assertThat(result.isPrimary()).isTrue();
    }

    @Test
    void addingASecondPhoneNumberIsNotPrimary() {
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(activeHolder());
        when(contactChannelRepository.findAllByAccountHolderIdAndSupersededByIsNullAndRevokedAtIsNull(holderId))
                .thenReturn(List.of());
        when(contactChannelRepository.findCurrentPrimary(holderId, ContactChannelType.PHONE.name()))
                .thenReturn(Optional.of(channel(ContactChannelType.PHONE, "+84900000000", true)));
        ContactChannel built = channel(ContactChannelType.PHONE, "+84912345678", false);
        when(contactChannelFactory.create(
                holderId, ContactChannelType.PHONE, ContactChannelPurpose.ACCOUNT,
                "+84912345678", "+84912345678", false))
                .thenReturn(built);
        when(contactChannelRepository.save(built)).thenReturn(built);

        ContactChannel result = service.add(
                holderId, ContactChannelType.PHONE, ContactChannelPurpose.ACCOUNT, "+84912345678");

        assertThat(result.isPrimary()).isFalse();
    }

    @Test
    void addingAMalformedPhoneNumberFailsBeforeTouchingTheRepository() {
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(activeHolder());

        assertThatThrownBy(() -> service.add(
                holderId, ContactChannelType.PHONE, ContactChannelPurpose.ACCOUNT, "not-a-phone"))
                .isInstanceOf(InvalidContactChannelValueException.class);
        verify(contactChannelRepository, never()).save(any());
    }

    @Test
    void addingADuplicateValueForTheSameHolderFails() {
        when(accountHolderFinder.findActiveById(holderId)).thenReturn(activeHolder());
        when(contactChannelRepository.findAllByAccountHolderIdAndSupersededByIsNullAndRevokedAtIsNull(holderId))
                .thenReturn(List.of(channel(ContactChannelType.PHONE, "+84912345678", true)));

        assertThatThrownBy(() -> service.add(
                holderId, ContactChannelType.PHONE, ContactChannelPurpose.ACCOUNT, "+84912345678"))
                .isInstanceOf(ContactChannelAlreadyRegisteredException.class);
        verify(contactChannelRepository, never()).save(any());
    }

    @Test
    void requestingVerificationForAnAlreadyVerifiedChannelFails() {
        ContactChannel verified = channel(ContactChannelType.PHONE, "+84912345678", false);
        verified.markVerified(NOW.minusSeconds(60), "SMS_CODE");
        when(contactChannelRepository.findById(verified.getId())).thenReturn(Optional.of(verified));

        assertThatThrownBy(() -> service.requestVerification(holderId, verified.getId()))
                .isInstanceOf(ContactChannelAlreadyVerifiedException.class);
    }

    @Test
    void requestingVerificationForAnUnknownChannelFails() {
        UUID channelId = UUID.randomUUID();
        when(contactChannelRepository.findById(channelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestVerification(holderId, channelId))
                .isInstanceOf(ContactChannelNotFoundException.class);
    }

    @Test
    void requestingVerificationForAnotherHoldersChannelFails() {
        ContactChannel someoneElses = channel(ContactChannelType.PHONE, "+84912345678", false);
        when(contactChannelRepository.findById(someoneElses.getId()))
                .thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.requestVerification(UUID.randomUUID(), someoneElses.getId()))
                .isInstanceOf(ContactChannelNotFoundException.class);
    }

    @Test
    void confirmingACorrectCodeVerifiesTheChannel() {
        ContactChannel pending = channel(ContactChannelType.PHONE, "+84912345678", false);
        when(contactChannelRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .type(AuthTokenType.CONTACT_CHANNEL_VERIFICATION)
                .tokenHash(HashUtils.sha256Hex("123456"))
                .expiresAt(NOW.plusSeconds(60))
                .channelId(pending.getId())
                .build();
        when(authTokenRepository.findByTokenHashAndType(
                HashUtils.sha256Hex("123456"), AuthTokenType.CONTACT_CHANNEL_VERIFICATION))
                .thenReturn(Optional.of(token));
        when(contactChannelRepository.save(pending)).thenReturn(pending);

        ContactChannel result = service.confirmVerification(holderId, pending.getId(), "123456");

        assertThat(result.isVerified()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingACodeIssuedForAnotherChannelFails() {
        ContactChannel pending = channel(ContactChannelType.PHONE, "+84912345678", false);
        when(contactChannelRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        AuthToken tokenForOtherChannel = AuthToken.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .type(AuthTokenType.CONTACT_CHANNEL_VERIFICATION)
                .tokenHash(HashUtils.sha256Hex("123456"))
                .expiresAt(NOW.plusSeconds(60))
                .channelId(UUID.randomUUID())
                .build();
        when(authTokenRepository.findByTokenHashAndType(
                HashUtils.sha256Hex("123456"), AuthTokenType.CONTACT_CHANNEL_VERIFICATION))
                .thenReturn(Optional.of(tokenForOtherChannel));

        assertThatThrownBy(() -> service.confirmVerification(holderId, pending.getId(), "123456"))
                .isInstanceOf(InvalidContactChannelVerificationCodeException.class);
    }

    @Test
    void removingThePrimaryChannelFails() {
        ContactChannel primary = channel(ContactChannelType.EMAIL, "person@example.com", true);
        when(contactChannelRepository.findById(primary.getId())).thenReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.remove(holderId, primary.getId()))
                .isInstanceOf(PrimaryContactChannelException.class);
        verify(contactChannelRepository, never()).save(any());
    }

    @Test
    void removingASecondaryChannelMarksItRemoved() {
        ContactChannel secondary = channel(ContactChannelType.PHONE, "+84912345678", false);
        when(contactChannelRepository.findById(secondary.getId())).thenReturn(Optional.of(secondary));
        when(contactChannelRepository.save(secondary)).thenReturn(secondary);

        service.remove(holderId, secondary.getId());

        assertThat(secondary.isRemoved()).isTrue();
    }

    private AccountHolder activeHolder() {
        return AccountHolder.builder()
                .id(holderId)
                .displayName("Test Holder")
                .status(AccountHolderStatus.ACTIVE)
                .build();
    }

    private ContactChannel channel(ContactChannelType type, String normalizedValue, boolean primary) {
        return ContactChannel.builder()
                .id(UUID.randomUUID())
                .accountHolderId(holderId)
                .channelType(type)
                .normalizedValue(normalizedValue)
                .originalValue(normalizedValue)
                .purpose(ContactChannelPurpose.ACCOUNT)
                .isPrimary(primary)
                .build();
    }
}
