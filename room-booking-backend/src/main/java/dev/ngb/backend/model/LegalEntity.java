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
 * A platform entity that is accountable for operating in one or more markets.
 *
 * <p>The entity is the counterparty a guest actually contracts with and the party that issues
 * invoices and remits tax. Which entity is accountable in which market, and for which span, lives in
 * {@code legal_entity_markets} so that replacing an entity does not rewrite the bookings the
 * previous one contracted.</p>
 *
 * <p>Registration and tax identifiers are held as references rather than values, because they are
 * sensitive and belong in protected storage.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("legal_entities")
public class LegalEntity {

    /** Primary key of the entity. */
    @Id
    private @Nullable UUID id;
    /** Stable operator-facing key, unique across entities. */
    private String entityKey;
    /** Registered legal name of the entity. */
    private String legalName;
    /** ISO 3166-1 alpha-2 country of incorporation. */
    private String countryCode;
    /** Reference to the protected company-registration record. */
    private @Nullable String registrationReference;
    /** Reference to the protected tax-identifier record. */
    private @Nullable String taxIdentifierRef;
    /** Registered address, held by reference so the address itself stays protected. */
    private @Nullable UUID registeredAddressRef;
    /** Whether the entity may be named as accountable by a domain decision. */
    private ConfigurationLifecycle lifecycleState;
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
