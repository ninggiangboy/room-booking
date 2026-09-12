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
 * A party's registration with one tax authority.
 *
 * <p>A registration number is a restricted identifier and never lands in this schema. Only a vault
 * token and a digest are stored: the digest lets a resubmitted number be recognised as the same one
 * without holding it, and the token lets the vault return the plaintext to the single caller entitled
 * to see it. At most the last four characters are kept, so a host can recognise their own
 * registration in a list.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("tax_registrations")
public class TaxRegistration {

    /** Primary key of the registration. */
    @Id
    private @Nullable UUID id;
    /** Tax profile this registration belongs to. */
    private UUID partyTaxProfileId;
    /** Authority's jurisdiction, as a country code with optional subdivisions. */
    private String jurisdictionCode;
    /** Which tax the party is registered for. */
    private TaxType taxType;
    /** Vault reference that can retrieve the plaintext number; never the number itself. */
    private String registrationToken;
    /** Lowercase hex SHA-256 of the number, for recognising a resubmission. */
    private String registrationDigest;
    /** Last few characters, so a host can identify their own registration. */
    private @Nullable String registrationLastFour;
    /** Where the verification evidence came from. */
    private @Nullable String verificationSource;
    /** Whether it has been confirmed with the authority. */
    private TaxRegistrationVerificationStatus verificationStatus;
    /** UTC instant it was confirmed; paired with a verified status. */
    private @Nullable Instant verifiedAt;
    /** UTC instant the registration took effect. */
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
