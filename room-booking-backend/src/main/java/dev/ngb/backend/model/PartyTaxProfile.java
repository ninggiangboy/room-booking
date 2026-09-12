package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * Where a party stands for tax purposes.
 *
 * <p>Residence and establishment decide which authority's rules apply to a party's income, so the
 * profile is effective-dated: a host who moves country does not retroactively change where last
 * year's stays were taxed. The database refuses two overlapping profiles for one party, because two
 * calculations a second apart could otherwise place them in two countries.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("party_tax_profiles")
public class PartyTaxProfile {

    /** Primary key of the profile. */
    @Id
    private @Nullable UUID id;
    /** Which kind of party this describes. */
    private TaxPartyType partyType;
    /** Account holder described, for an account-holder profile. */
    private @Nullable UUID accountHolderId;
    /** Legal entity described, for a legal-entity profile. */
    private @Nullable UUID legalEntityId;
    /** Whether the party is taxed as a person or as a business. */
    private TaxEntityType entityType;
    /** ISO 3166-1 alpha-2 country the party is tax-resident in. */
    private String residenceCountry;
    /** Country of a fixed establishment, when one differs from residence. */
    private @Nullable String establishmentCountry;
    /** How much of this the platform has actually verified. */
    private TaxProfileStatus status;
    /** UTC instant the profile took effect. */
    private Instant effectiveFrom;
    /** UTC instant it stopped applying; absent while current. */
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
