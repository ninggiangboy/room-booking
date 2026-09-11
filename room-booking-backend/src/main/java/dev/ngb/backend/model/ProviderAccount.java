package dev.ngb.backend.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An approved external integration context: which provider, for which capability, in which market.
 *
 * <p>Credentials are never stored here. {@link #secretReference} and {@link #webhookSecretRef} name
 * entries in a secret manager, so a database dump is not a credential leak and an event or log line
 * quoting the account key discloses nothing.</p>
 *
 * <p>{@link #accountVersion} increments on credential rotation and is part of the account's unique
 * identity, which lets a provider resource recorded under an older credential still be resolved.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("provider_accounts")
public class ProviderAccount {

    /** Primary key of the account. */
    @Id
    private @Nullable UUID id;
    /** Stable operator-facing key for the integration. */
    private String accountKey;
    /** Configuration version, incremented on credential rotation. */
    private short accountVersion;
    /** Provider family, such as the payment or messaging vendor. */
    private String providerFamily;
    /** Capability the account is approved for, such as {@code CARD_PAYMENT}. */
    private String capability;
    /** Market the account is scoped to; {@code null} when it serves every market. */
    private @Nullable UUID marketId;
    /** Legal entity the account transacts on behalf of. */
    private @Nullable UUID legalEntityId;
    /** ISO 4217 currency the account is approved for, where it is currency-specific. */
    private @Nullable String currency;
    /** Whether the account addresses the provider's sandbox or its live environment. */
    private ProviderEnvironment environment;
    /** Whether the account may be selected by an adapter. */
    private ConfigurationLifecycle lifecycleState;
    /** Secret-manager reference for the API credential; never the credential itself. */
    private String secretReference;
    /** Secret-manager reference for the webhook signing key. */
    private @Nullable String webhookSecretRef;
    /** UTC instant from which the account may be used, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant from which it may not, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
